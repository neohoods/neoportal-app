# Neohoods - portal

## Introduction

Neohoods - Portal

## Development Setup

### Prerequisites

Before setting up the project, ensure you have the following tools installed:

- **Docker**: Used for containerizing the application and managing dependencies.
- **Angular CLI**: Required for building and serving the Angular frontend.
- **Java 21**: The backend is built using Java 21, so ensure this version is installed.

### Environment Variables

To run the project, you'll need specific environment variables. Please contact the project owner to obtain the development values for these variables:

- `SENDGRID_API_KEY`: Used for sending emails through the SendGrid service.

### Hostnames

Configure the following hostname locally by editing your `/etc/hosts` file:

```bash
127.0.0.1   local.portal.neohoods.com
```

This setup allows the application to be accessed locally via a custom domain.

## Backend

### Running the Database

The application uses a PostgreSQL database, which can be managed using Docker. To start the database, use the following command:

```bash
docker-compose down; docker-compose up
```

- **Note**: Running `docker-compose down` will reset the database. If you wish to continue without resetting, simply use `docker-compose up`.

### Running the Backend

It is recommended to run the backend using IntelliJ IDEA for better integration and debugging capabilities. The main class to run is `com.neohoods.portal.platform.Main`. The backend operates on port `8486`.

## Frontend

### Running the Frontend

To start the frontend, execute:

```bash
ng serve --configuration=mock-test
```

This command will serve the application using a mock backend for testing purposes.

### Environments

- **Development**: Run both backend and frontend together for a full-stack development experience.
- **Mock-Test**: Run the frontend only, using a mock backend to simulate API responses.

The frontend is accessible via a self-signed certificate at `http://local.portal.neohoods.com:4200/`. It proxies requests to the backend through `/api` (e.g., `http://local.portal.neohoods.com:4200/api`).

## Architecture

The project follows an API-first approach using OpenAPI. Both the frontend and backend rely on `openapi.yml` to serve or consume the API. The backend generates the server API using Maven, while the frontend generates the SDK using the `openapi-generator-cli`.

To modify the API, such as adding endpoints, modifying responses, or adding parameters, you must first update the `openapi.yml`.

## Licensing

This project is licensed under the Apache 2.0 License, which allows for open-source use and distribution.
