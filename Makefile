ROOT_DIR := $(shell pwd)
VERSION  := $(shell mvn -f pom.xml help:evaluate -Dexpression=project.version -q -DforceStdout 2>/dev/null || echo "0.0.1-SNAPSHOT")
PACKAGE_NAME := hify-$(VERSION)
PACKAGE_DIR  := $(ROOT_DIR)/dist/$(PACKAGE_NAME)

.PHONY: start stop restart build clean package

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
	@rm -rf $(ROOT_DIR)/logs $(ROOT_DIR)/.pids
	@echo "[CLEAN] 清理打包目录..."
	@rm -rf $(ROOT_DIR)/dist
	@echo "[CLEAN] 完成"

package: build
	@echo "[PACKAGE] 打包 $(PACKAGE_NAME) ..."
	@mkdir -p $(PACKAGE_DIR)
	@JAR=$$(find $(ROOT_DIR)/hify-app/target -maxdepth 1 -name "*.jar" ! -name "*sources*" | head -1); \
	  cp "$$JAR" $(PACKAGE_DIR)/hify-app.jar
	@cp -r $(ROOT_DIR)/hify-web/dist $(PACKAGE_DIR)/web
	@cp $(ROOT_DIR)/hify-app/src/main/resources/application.yml $(PACKAGE_DIR)/application.yml
	@cp $(ROOT_DIR)/start.sh $(ROOT_DIR)/stop.sh $(PACKAGE_DIR)/
	@chmod +x $(PACKAGE_DIR)/start.sh $(PACKAGE_DIR)/stop.sh
	@cd $(ROOT_DIR)/dist && tar -czf $(PACKAGE_NAME).tar.gz $(PACKAGE_NAME)
	@echo "[PACKAGE] 产物：$(ROOT_DIR)/dist/$(PACKAGE_NAME).tar.gz"
