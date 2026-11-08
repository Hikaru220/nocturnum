//! SunShine server banner: Server List Ping (online + latency), on demand only.

use serde::Serialize;
use tokio::io::{AsyncReadExt, AsyncWriteExt};

/// Public entrance of the server (BungeeCord proxy; the same host is 185.9.145.165:30040 by IP).
pub const ADDRESS: &str = "play.sunshine-mc.ru:30040";
const HOST_NAME: &str = "play.sunshine-mc.ru";

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Status {
    pub online: i64,
    pub max: i64,
    pub ping_ms: u64,
    pub address: String,
}

fn varint(mut v: u32, out: &mut Vec<u8>) {
    loop {
        let b = (v & 0x7F) as u8;
        v >>= 7;
        if v == 0 {
            out.push(b);
            return;
        }
        out.push(b | 0x80);
    }
}

async fn read_varint(s: &mut tokio::net::TcpStream) -> std::io::Result<u32> {
    let mut v = 0u32;
    for i in 0..5 {
        let b = s.read_u8().await?;
        v |= ((b & 0x7F) as u32) << (7 * i);
        if b & 0x80 == 0 {
            return Ok(v);
        }
    }
    Err(std::io::Error::other("bad varint"))
}

pub async fn ping() -> Option<Status> {
    let (_, port) = ADDRESS.split_once(':')?;
    let host = HOST_NAME;
    let t0 = std::time::Instant::now();
    let work = async {
        let mut s = tokio::net::TcpStream::connect(ADDRESS).await?;
        let mut hs = vec![0u8];
        varint(767, &mut hs);
        varint(host.len() as u32, &mut hs);
        hs.extend_from_slice(host.as_bytes());
        hs.extend_from_slice(&port.parse::<u16>().unwrap_or(25565).to_be_bytes());
        varint(1, &mut hs);
        let mut pkt = Vec::new();
        varint(hs.len() as u32, &mut pkt);
        pkt.extend(hs);
        pkt.extend([1u8, 0u8]);
        s.write_all(&pkt).await?;
        let _len = read_varint(&mut s).await?;
        let _id = read_varint(&mut s).await?;
        let n = read_varint(&mut s).await? as usize;
        let mut buf = vec![0u8; n.min(1 << 20)];
        s.read_exact(&mut buf).await?;
        Ok::<Vec<u8>, std::io::Error>(buf)
    };
    let buf = tokio::time::timeout(std::time::Duration::from_secs(5), work).await.ok()?.ok()?;
    let v: serde_json::Value = serde_json::from_slice(&buf).ok()?;
    Some(Status {
        online: v["players"]["online"].as_i64().unwrap_or(0),
        max: v["players"]["max"].as_i64().unwrap_or(0),
        ping_ms: t0.elapsed().as_millis() as u64,
        address: ADDRESS.to_string(),
    })
}
