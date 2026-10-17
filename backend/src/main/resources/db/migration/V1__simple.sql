CREATE TABLE game_entity (
 id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin PRIMARY KEY,
 type VARCHAR(40) NOT NULL, name VARCHAR(255) NOT NULL, content JSON NOT NULL,
 INDEX entity_type(type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE entity_alias (
 entity_id VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 name VARCHAR(191) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
 PRIMARY KEY(entity_id,name), INDEX alias_name(name),
 FOREIGN KEY(entity_id) REFERENCES game_entity(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE data_import (
 id INT PRIMARY KEY, dataset_id VARCHAR(191), game_version VARCHAR(255), content_hash CHAR(64),
 embedding_model VARCHAR(191), dimension INT, status VARCHAR(16) NOT NULL,
 entity_count INT NOT NULL DEFAULT 0, guide_count INT NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO data_import(id,status) VALUES(1,'EMPTY');
CREATE TABLE chat_session (
 id CHAR(36) PRIMARY KEY, title VARCHAR(120) NOT NULL DEFAULT '新对话',
 created_at DATETIME(6) NOT NULL, updated_at DATETIME(6) NOT NULL,
 INDEX history_order(updated_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE chat_message (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, session_id CHAR(36) NOT NULL,
 role VARCHAR(16) NOT NULL, content MEDIUMTEXT NOT NULL,
 tool_results JSON NOT NULL, sources JSON NOT NULL, created_at DATETIME(6) NOT NULL,
 FOREIGN KEY(session_id) REFERENCES chat_session(id), INDEX message_history(session_id,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
