use anyhow::{Context, Result};
use std::fs::File;
use std::path::Path;

pub fn export(session_root: &Path, dest_path: &Path) -> Result<()> {
    let out = File::create(dest_path)
        .with_context(|| format!("failed to create export file {:?}", dest_path))?;
    let encoder = zstd::Encoder::new(out, 3)?.auto_finish();
    let mut ar = tar::Builder::new(encoder);
    ar.append_dir_all(".", session_root)
        .context("failed to append session root to archive")?;
    ar.finish().context("failed to finalize archive")?;
    Ok(())
}

pub fn import(src_path: &Path, dest_root: &Path) -> Result<()> {
    std::fs::create_dir_all(dest_root)
        .with_context(|| format!("failed to create dest dir {:?}", dest_root))?;
    let src = File::open(src_path)
        .with_context(|| format!("failed to open import file {:?}", src_path))?;
    let decoder = zstd::Decoder::new(src)?;
    let mut ar = tar::Archive::new(decoder);
    ar.unpack(dest_root)
        .context("failed to unpack archive")?;
    Ok(())
}
