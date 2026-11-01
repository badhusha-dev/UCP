# Developer Onboarding Guide

**Universal Commerce Platform**  
*Document Version:* 2.0.0

---

## 1. Local Development Environment Setup

### Required Tools
- **JDK 17 LTS or 21 LTS** (Eclipse Temurin recommended)
- **Node.js 20 LTS** and **npm 10**
- **Docker Desktop** or **OrbStack** with at least 8 GB allocated RAM
- **IDE**: IntelliJ IDEA (Ultimate or Community) + VS Code

### IDE Configuration
1. **Lombok Plugin**: Ensure the Lombok plugin is enabled in IntelliJ, and turn on **Annotation Processing** under `Preferences > Build, Execution, Deployment > Compiler > Annotation Processors`.
2. **Node Workspace**: In VS Code, open the `frontend/` directory to enable workspace TypeScript definitions.

---

## 2. Recommended Day-to-Day Development Workflow

1. **Start Backing Services Only**:
   ```bash
   docker compose up -d postgres redis zookeeper kafka discovery-server config-server
   ```
2. **Run Microservice Under Active Development in IDE**:
   - Set active Spring profile to `dev` or `docker`.
   - Run `Application.java` directly in debug mode with breakpoints.
3. **Run Microfrontend in Dev Mode**:
   ```bash
   cd frontend/mfe-product && npm run dev
   # Changes reflect instantly via Vite HMR on http://localhost:3001
   ```
4. **Run Smoke Tests Before Pushing**:
   ```bash
   bash scripts/smoke-test.sh
   ```
