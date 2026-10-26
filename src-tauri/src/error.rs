use serde::{Serialize, Serializer};

#[derive(Debug)]
pub struct AppError(pub String);

impl std::fmt::Display for AppError {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl Serialize for AppError {
    fn serialize<S: Serializer>(&self, s: S) -> Result<S::Ok, S::Error> {
        s.serialize_str(&self.0)
    }
}

impl<E: Into<anyhow::Error>> From<E> for AppError {
    fn from(e: E) -> Self {
        let e: anyhow::Error = e.into();
        AppError(format!("{:#}", e))
    }
}

impl AppError {
    pub fn ctx(self, m: &str) -> Self {
        AppError(format!("{m}: {}", self.0))
    }
}

pub type R<T> = Result<T, AppError>;
