-- Machine-JWT теперь auto-provisioned и валидируется по актуальной записи units.
-- Старый реестр ручных токенов больше не является частью security-контракта.
DROP TABLE IF EXISTS machine_tokens;