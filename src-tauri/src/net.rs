use crate::error::R;
use anyhow::{anyhow, Context};
use futures_util::StreamExt;
use once_cell::sync::Lazy;
use serde::Serialize;
use sha1::{Digest, Sha1};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use tauri::{AppHandle, Emitter};
use tokio::io::AsyncWriteExt;
use tokio::sync::Semaphore;

pub const USER_AGENT: &str = concat!("Hikaru220/nocturnum/", env!("CARGO_PKG_VERSION"), " (github.com/Hikaru220/nocturnum)");

pub static CLIENT: Lazy<reqwest::Client> = Lazy::new(|| {
    reqwest::Client::builder()
        .user_agent(USER_AGENT)
        .connect_timeout(std::time::Duration::from_secs(15))
        .timeout(std::time::Duration::from_secs(900))
        .build()
        .expect("http client")
});

pub async fn get_json<T: serde::de::DeserializeOwned>(url: &str) -> R<T> {
    let resp = CLIENT.get(url).send().await.with_context(|| format!("GET {url}"))?;
    let status = resp.status();
    if !status.is_success() {
        return Err(anyhow!("{url}: HTTP {status}").into());
    }
    Ok(resp.json::<T>().await.with_context(|| format!("parse {url}"))?)
}

pub async fn get_text(url: &str) -> R<String> {
    let resp = CLIENT.get(url).send().await?;
    if !resp.status().is_success() {
        return Err(anyhow!("{url}: HTTP {}", resp.status()).into());
    }
    Ok(resp.text().await?)
}

#[derive(Clone, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
    pub id: String,
    pub label: String,
    pub percent: f64,
    pub speed: f64,
    pub done: bool,
    pub error: Option<String>,
}

pub fn emit_progress(app: &AppHandle, id: &str, label: &str, percent: f64, speed: f64, done: bool, error: Option<String>) {
    let _ = app.emit("progress", Progress { id: id.into(), label: label.into(), percent, speed, done, error });
}

#[derive(Clone)]
pub enum Hash {
    Sha1(String),
    Sha512(String),
    None,
}

pub fn file_sha1(path: &Path) -> std::io::Result<String> {
    use std::io::Read;
    let mut f = std::fs::File::open(path)?;
    let mut h = Sha1::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(hex::encode(h.finalize()))
}

pub fn file_sha512(path: &Path) -> std::io::Result<String> {
    use sha2::{Digest as _, Sha512};
    use std::io::Read;
    let mut f = std::fs::File::open(path)?;
    let mut h = Sha512::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(hex::encode(h.finalize()))
}

pub fn verify(path: &Path, hash: &Hash) -> bool {
    if !path.exists() {
        return false;
    }
    match hash {
        Hash::None => true,
        Hash::Sha1(h) => file_sha1(path).map(|x| x.eq_ignore_ascii_case(h)).unwrap_or(false),
        Hash::Sha512(h) => file_sha512(path).map(|x| x.eq_ignore_ascii_case(h)).unwrap_or(false),
    }
}

/// Download a single file with retries, verifying the hash. Writes to `.part` then renames.
pub async fn download_file(url: &str, dest: &Path, hash: &Hash, counter: Option<&AtomicU64>) -> R<()> {
    if !matches!(hash, Hash::None) && verify(dest, hash) {
        return Ok(());
    }
    if let Some(parent) = dest.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let part: PathBuf = {
        let mut p = dest.as_os_str().to_owned();
        p.push(".part");
        PathBuf::from(p)
    };
    let mut last_err = anyhow!("unknown");
    for attempt in 0..4u64 {
        if attempt > 0 {
            tokio::time::sleep(std::time::Duration::from_millis(500 * attempt)).await;
        }
        let res: anyhow::Result<()> = async {
            let resp = CLIENT.get(url).send().await?;
            if !resp.status().is_success() {
                return Err(anyhow!("HTTP {}", resp.status()));
            }
            let mut file = tokio::fs::File::create(&part).await?;
            let mut stream = resp.bytes_stream();
            while let Some(chunk) = stream.next().await {
                let chunk = chunk?;
                file.write_all(&chunk).await?;
                if let Some(c) = counter {
                    c.fetch_add(chunk.len() as u64, Ordering::Relaxed);
                }
            }
            file.flush().await?;
            drop(file);
            if !verify(&part, hash) {
                return Err(anyhow!("hash mismatch"));
            }
            tokio::fs::rename(&part, dest).await?;
            Ok(())
        }
        .await;
        match res {
            Ok(()) => return Ok(()),
            Err(e) => last_err = e.context(format!("download {url}")),
        }
    }
    let _ = tokio::fs::remove_file(&part).await;
    Err(last_err.into())
}

pub struct Job {
    pub url: String,
    pub dest: PathBuf,
    pub hash: Hash,
    pub size: u64,
}

/// Parallel download of many files with progress events.
pub async fn download_many(app: &AppHandle, id: &str, label: &str, jobs: Vec<Job>, threads: usize) -> R<()> {
    let jobs: Vec<Job> = jobs
        .into_iter()
        .filter(|j| matches!(j.hash, Hash::None) || !verify(&j.dest, &j.hash))
        .collect();
    if jobs.is_empty() {
        return Ok(());
    }
    let total: u64 = jobs.iter().map(|j| j.size.max(1)).sum();
    let counter = Arc::new(AtomicU64::new(0));
    let sem = Arc::new(Semaphore::new(threads.clamp(1, 32)));

    let reporter = {
        let (app, id, label, counter) = (app.clone(), id.to_string(), label.to_string(), counter.clone());
        tokio::spawn(async move {
            let mut last = 0u64;
            loop {
                tokio::time::sleep(std::time::Duration::from_millis(250)).await;
                let cur = counter.load(Ordering::Relaxed);
                let speed = cur.saturating_sub(last) as f64 * 4.0;
                last = cur;
                let pct = (cur as f64 / total as f64 * 100.0).min(99.0);
                emit_progress(&app, &id, &label, pct, speed, false, None);
            }
        })
    };

    let mut handles = Vec::new();
    for job in jobs {
        let sem = sem.clone();
        let counter = counter.clone();
        handles.push(tokio::spawn(async move {
            let _p = sem.acquire().await.map_err(|e| anyhow!(e))?;
            download_file(&job.url, &job.dest, &job.hash, Some(&counter))
                .await
                .map_err(|e| anyhow!(e.0))
        }));
    }
    let mut first_err: Option<anyhow::Error> = None;
    for h in handles {
        match h.await {
            Ok(Ok(())) => {}
            Ok(Err(e)) => {
                if first_err.is_none() {
                    first_err = Some(e);
                }
            }
            Err(e) => {
                if first_err.is_none() {
                    first_err = Some(anyhow!(e));
                }
            }
        }
    }
    reporter.abort();
    match first_err {
        Some(e) => Err(e.into()),
        None => Ok(()),
    }
}
