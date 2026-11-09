//! NOCTURNUM: changing the skin of a licensed account through the core (our skin editor and library stay ours).

use crate::core::err;
use crate::error::R;
use anyhow::anyhow;
use theseus::minecraft_skins::{Bytes, MinecraftSkinVariant};

/// UUID of an account (the interface uses the UUID as the account id).
pub async fn uuid_of(id: &str) -> R<String> {
    let users = theseus::minecraft_auth::users().await.map_err(err)?;
    users.iter().find(|c| c.offline_profile.id.to_string() == id).map(|c| c.offline_profile.id.to_string()).ok_or_else(|| anyhow!("Аккаунт не найден").into())
}

/// Equips a skin for one account (the core works with the default account, so it is selected first).
pub async fn upload(account_id: &str, png: Vec<u8>, slim: bool) -> R<()> {
    let uuid = uuid::Uuid::parse_str(account_id).map_err(|e| anyhow!("{e}"))?;
    theseus::minecraft_auth::set_default_user(uuid).await.map_err(err)?;
    let variant = if slim { MinecraftSkinVariant::Slim } else { MinecraftSkinVariant::Classic };
    theseus::minecraft_skins::add_and_equip_custom_skin(Bytes::from(png), variant, None).await.map_err(err)?;
    Ok(())
}
