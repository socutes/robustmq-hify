ROOT_DIR     := $(shell pwd)
VERSION      := $(shell mvn -f pom.xml help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "0.0.1-SNAPSHOT")
PACKAGE_NAME := hify-$(VERSION)
PACKAGE_DIR  := $(ROOT_DIR)/dist/$(PACKAGE_NAME)
TARBALL      := $(ROOT_DIR)/dist/$(PACKAGE_NAME).tar.gz

.PHONY: start stop restart build build-backend build-frontend clean package

start:
	@bash $(ROOT_DIR)/start.sh

stop:
	@bash $(ROOT_DIR)/stop.sh

restart: stop start

build: build-backend build-frontend

build-backend:
	@echo "[BUILD] 构建后端..."
	@mvn -f $(ROOT_DIR)/pom.xml clean package -DskipTests -q
	@echo "[BUILD] 后端构建完成"

build-frontend:
	@echo "[BUILD] 构建前端..."
	@npm --prefix $(ROOT_DIR)/hify-web install --silent
	@npm --prefix $(ROOT_DIR)/hify-web run build
	@echo "[BUILD] 前端构建完成"

clean:
	@echo "[CLEAN] 清理后端构建产物..."
	@mvn -f $(ROOT_DIR)/pom.xml clean -q
	@echo "[CLEAN] 清理前端构建产物..."
	@rm -rf $(ROOT_DIR)/hify-web/dist
	@echo "[CLEAN] 清理日志和 PID..."
	@rm -rf $(ROOT_DIR)/logs $(ROOT_DIR)/.pids $(ROOT_DIR)/hify.pid
	@echo "[CLEAN] 清理打包目录..."
	@rm -rf $(ROOT_DIR)/dist
	@echo "[CLEAN] 完成"

# ── 打包为 tar.gz ─────────────────────────────────────────────
# 产物结构：
#   hify-<version>/
#   ├── hify-app.jar          后端 fat jar
#   ├── web/                  前端静态文件（由 Nginx 托管）
#   ├── start.sh              启动脚本
#   ├── stop.sh               停止脚本
#   ├── application.yml       配置文件（已含环境变量占位符）
#   └── env.template          环境变量模板，复制为 .env 后填写
package: build
	@echo "[PACKAGE] 打包 $(PACKAGE_NAME) ..."
	@rm -rf $(PACKAGE_DIR) && mkdir -p $(PACKAGE_DIR)

	@JAR=$$(find $(ROOT_DIR)/hify-app/target -maxdepth 1 -name "*.jar" ! -name "*sources*" | head -1); \
	  [ -f "$$JAR" ] || (echo "[ERROR] 找不到 jar 文件" && exit 1); \
	  cp "$$JAR" $(PACKAGE_DIR)/hify-app.jar

	@cp -r $(ROOT_DIR)/hify-web/dist $(PACKAGE_DIR)/web
	@cp $(ROOT_DIR)/start.sh $(PACKAGE_DIR)/start.sh
	@cp $(ROOT_DIR)/stop.sh  $(PACKAGE_DIR)/stop.sh
	@chmod +x $(PACKAGE_DIR)/start.sh $(PACKAGE_DIR)/stop.sh
	@cp $(ROOT_DIR)/deploy/application.yml.template $(PACKAGE_DIR)/application.yml
	@cp $(ROOT_DIR)/deploy/env.template             $(PACKAGE_DIR)/env.template

	@cd $(ROOT_DIR)/dist && tar -czf $(PACKAGE_NAME).tar.gz $(PACKAGE_NAME)
	@rm -rf $(PACKAGE_DIR)

	@echo "[PACKAGE] 完成：$(TARBALL)"
	@echo "[PACKAGE] 大小：$$(du -sh $(TARBALL) | cut -f1)"
