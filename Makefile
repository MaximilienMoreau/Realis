SHELL := /bin/bash
export PATH := /usr/local/bin:/usr/bin:/bin:$(PATH)

.PHONY: dev dev-reset stop help

help:
	@echo ""
	@echo "  make dev        — lance postgres + backend + frontend"
	@echo "  make dev-reset  — remet la DB à zéro et relance (résout les erreurs de mot de passe)"
	@echo "  make stop       — arrête tout"
	@echo ""

dev:
	@bash dev.sh

dev-reset:
	@bash -c "docker compose down -v"
	@$(MAKE) dev

stop:
	@bash -c "docker compose down"
	@pkill -f "spring-boot:run" 2>/dev/null || true
	@pkill -f "next dev"        2>/dev/null || true
