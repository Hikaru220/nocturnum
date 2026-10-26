//! Media player bridge (ТЗ пакет 2, п. 5.1): the launcher reads the current track from Windows
//! System Media Transport Controls and serves it to the HUD mod over 127.0.0.1 only.
//! Every request needs a random per-launch token, so web pages cannot talk to it.

use once_cell::sync::OnceCell;
use serde::Serialize;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::sync::mpsc::{channel, Receiver, Sender};
use std::sync::{Arc, Mutex};

#[derive(Serialize, Clone, Default, Debug)]
#[serde(rename_all = "camelCase")]
pub struct MediaState {
    pub available: bool,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub position_ms: u64,
    pub duration_ms: u64,
    pub playing: bool,
    /// changes whenever the track changes, so the mod knows to refetch the cover
    pub track_id: u64,
}

#[derive(Clone)]
pub struct Bridge {
    pub port: u16,
    pub token: String,
}

static BRIDGE: OnceCell<Bridge> = OnceCell::new();

pub fn bridge() -> Option<Bridge> {
    BRIDGE.get().cloned()
}

struct Shared {
    state: MediaState,
    cover: Vec<u8>,
}

enum Cmd {
    Play,
    Pause,
    Toggle,
    Next,
    Prev,
}

/// Starts the poller and the local server once. Safe to call repeatedly.
pub fn start() {
    if BRIDGE.get().is_some() {
        return;
    }
    let listener = match TcpListener::bind("127.0.0.1:0") {
        Ok(l) => l,
        Err(_) => return,
    };
    let port = match listener.local_addr() {
        Ok(a) => a.port(),
        Err(_) => return,
    };
    let token = uuid::Uuid::new_v4().simple().to_string();
    let _ = BRIDGE.set(Bridge { port, token: token.clone() });

    let shared = Arc::new(Mutex::new(Shared { state: MediaState::default(), cover: vec![] }));
    let (tx, rx) = channel::<Cmd>();

    let s2 = shared.clone();
    std::thread::spawn(move || poll_loop(s2, rx));

    std::thread::spawn(move || {
        for stream in listener.incoming().flatten() {
            let shared = shared.clone();
            let token = token.clone();
            let tx = tx.clone();
            std::thread::spawn(move || {
                let _ = handle(stream, &shared, &token, &tx);
            });
        }
    });
}

fn handle(mut s: TcpStream, shared: &Arc<Mutex<Shared>>, token: &str, tx: &Sender<Cmd>) -> std::io::Result<()> {
    s.set_read_timeout(Some(std::time::Duration::from_secs(2)))?;
    let mut buf = [0u8; 2048];
    let n = s.read(&mut buf)?;
    let req = String::from_utf8_lossy(&buf[..n]).to_string();
    let first = req.lines().next().unwrap_or("");
    let mut parts = first.split_whitespace();
    let method = parts.next().unwrap_or("");
    let path = parts.next().unwrap_or("");
    let authorized = req.lines().any(|l| {
        let l = l.to_ascii_lowercase();
        l.starts_with("x-nocturnum-token:") && l.trim_start_matches("x-nocturnum-token:").trim() == token
    });
    if !authorized {
        return respond(&mut s, 401, "text/plain", b"unauthorized");
    }
    match (method, path) {
        ("GET", "/state") => {
            let st = shared.lock().unwrap().state.clone();
            respond(&mut s, 200, "application/json", serde_json::to_string(&st).unwrap_or_default().as_bytes())
        }
        ("GET", "/cover") => {
            let c = shared.lock().unwrap().cover.clone();
            if c.is_empty() {
                respond(&mut s, 404, "text/plain", b"no cover")
            } else {
                respond(&mut s, 200, "application/octet-stream", &c)
            }
        }
        ("POST", p) if p.starts_with("/cmd/") => {
            let cmd = match &p[5..] {
                "play" => Some(Cmd::Play),
                "pause" => Some(Cmd::Pause),
                "toggle" => Some(Cmd::Toggle),
                "next" => Some(Cmd::Next),
                "prev" => Some(Cmd::Prev),
                _ => None,
            };
            match cmd {
                Some(c) => {
                    let _ = tx.send(c);
                    respond(&mut s, 200, "text/plain", b"ok")
                }
                None => respond(&mut s, 404, "text/plain", b"unknown"),
            }
        }
        _ => respond(&mut s, 404, "text/plain", b"not found"),
    }
}

fn respond(s: &mut TcpStream, code: u16, ctype: &str, body: &[u8]) -> std::io::Result<()> {
    let status = match code {
        200 => "OK",
        401 => "Unauthorized",
        _ => "Not Found",
    };
    write!(s, "HTTP/1.1 {code} {status}\r\nContent-Type: {ctype}\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len())?;
    s.write_all(body)
}

#[cfg(windows)]
fn poll_loop(shared: Arc<Mutex<Shared>>, rx: Receiver<Cmd>) {
    use windows::Media::Control::{GlobalSystemMediaTransportControlsSessionManager as Mgr, GlobalSystemMediaTransportControlsSessionPlaybackStatus as Status};
    use windows::Storage::Streams::DataReader;

    let mut last_key = String::new();
    let mut track_id = 0u64;
    loop {
        let result: windows::core::Result<()> = (|| {
            let mgr = Mgr::RequestAsync()?.get()?;
            let session = mgr.GetCurrentSession()?;
            // commands first, so buttons feel instant
            while let Ok(c) = rx.try_recv() {
                let _ = match c {
                    Cmd::Play => session.TryPlayAsync()?.get(),
                    Cmd::Pause => session.TryPauseAsync()?.get(),
                    Cmd::Toggle => session.TryTogglePlayPauseAsync()?.get(),
                    Cmd::Next => session.TrySkipNextAsync()?.get(),
                    Cmd::Prev => session.TrySkipPreviousAsync()?.get(),
                };
            }
            let props = session.TryGetMediaPropertiesAsync()?.get()?;
            let tl = session.GetTimelineProperties()?;
            let status = session.GetPlaybackInfo()?.PlaybackStatus()?;
            let title = props.Title()?.to_string();
            let artist = props.Artist()?.to_string();
            let album = props.AlbumTitle()?.to_string();
            let key = format!("{title}\u{1}{artist}\u{1}{album}");
            let mut cover: Option<Vec<u8>> = None;
            if key != last_key {
                last_key = key;
                track_id += 1;
                cover = Some(
                    (|| -> windows::core::Result<Vec<u8>> {
                        let stream = props.Thumbnail()?.OpenReadAsync()?.get()?;
                        let size = stream.Size()?.min(2_000_000) as u32;
                        let reader = DataReader::CreateDataReader(&stream.GetInputStreamAt(0)?)?;
                        reader.LoadAsync(size)?.get()?;
                        let mut bytes = vec![0u8; size as usize];
                        reader.ReadBytes(&mut bytes)?;
                        Ok(bytes)
                    })()
                    .unwrap_or_default(),
                );
            }
            let ticks = |t: windows::Foundation::TimeSpan| (t.Duration.max(0) / 10_000) as u64; // 100 ns -> ms
            let st = MediaState {
                available: !title.is_empty() || !artist.is_empty(),
                title,
                artist,
                album,
                position_ms: ticks(tl.Position()?),
                duration_ms: ticks(tl.EndTime()?).saturating_sub(ticks(tl.StartTime()?)),
                playing: status == Status::Playing,
                track_id,
            };
            let mut g = shared.lock().unwrap();
            g.state = st;
            if let Some(c) = cover {
                g.cover = c;
            }
            Ok(())
        })();
        if result.is_err() {
            // no active session (nothing playing): hide the plate
            let mut g = shared.lock().unwrap();
            g.state = MediaState::default();
            while rx.try_recv().is_ok() {}
        }
        std::thread::sleep(std::time::Duration::from_millis(700));
    }
}

#[cfg(not(windows))]
fn poll_loop(_shared: Arc<Mutex<Shared>>, rx: Receiver<Cmd>) {
    // Linux (MPRIS over D-Bus) and macOS are planned; for now the plate stays hidden.
    loop {
        while rx.try_recv().is_ok() {}
        std::thread::sleep(std::time::Duration::from_secs(2));
    }
}
