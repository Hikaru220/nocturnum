//! NOCTURNUM: Java search and check through the core (the core also downloads Eclipse Temurin by itself on install).

use crate::core::err;
use crate::{error::R, java::JavaInfo};

fn to_info(j: &theseus::data::JavaVersion) -> JavaInfo {
    JavaInfo {
        path: j.path.clone(),
        major: j.parsed_version,
        version: j.version.clone(),
        managed: j.path.to_lowercase().contains("java_versions"),
    }
}

pub async fn detect() -> R<Vec<JavaInfo>> {
    let mut v: Vec<JavaInfo> = theseus::jre::find_filtered_jres(None).await.map_err(err)?.iter().map(to_info).collect();
    v.sort_by(|a, b| b.major.cmp(&a.major));
    v.dedup_by(|a, b| a.path.eq_ignore_ascii_case(&b.path));
    Ok(v)
}

pub async fn validate(path: &str) -> R<JavaInfo> {
    let j = theseus::jre::check_jre(path.into()).await.map_err(err)?;
    Ok(to_info(&j))
}
