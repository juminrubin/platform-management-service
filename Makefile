.PHONY: backend-run backend-test frontend-dev frontend-build frontend-test compose-up compose-down tokens-help test

backend-run:
	cd backend && mvn spring-boot:run

backend-test:
	cd backend && mvn test

frontend-dev:
	cd frontend && npm run dev

frontend-build:
	cd frontend && npm run build

frontend-test:
	cd frontend && npm test

test: backend-test frontend-test

compose-up:
	docker compose -f deploy/docker-compose.yml up --build

compose-down:
	docker compose -f deploy/docker-compose.yml down

tokens-help:
	@echo "From repo root: APP_AZURE_TENANT_ID + APP_API_CLIENT_ID"
	@echo "  ./scripts/get-token-human.sh"
	@echo "  ./backend/scripts/get-token-mi.sh"
