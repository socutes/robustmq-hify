-- mock profile 测试数据，仅用于本地开发验证

INSERT INTO provider(name, type, base_url, auth_config, description, enabled, deleted, created_at, updated_at)
VALUES ('OpenAI', 'OPENAI', 'https://api.openai.com', '{"apiKey":"sk-test"}', '', 1, 0, NOW(), NOW());

INSERT INTO model_config(provider_id, name, model_id, context_size, enabled, deleted, created_at, updated_at)
VALUES (1, 'GPT-4o', 'gpt-4o', 128000, 1, 0, NOW(), NOW());
