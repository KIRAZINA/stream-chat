# Development Setup

## Prerequisites

- Java 17+
- Node.js 18+ (for frontend)
- Maven 3.8+
- Docker & Docker Compose (optional, for production)

## Backend Setup

1. Navigate to project root
2. Run `mvn spring-boot:run`
3. Backend runs on `http://localhost:8080`
4. Access Swagger UI at `http://localhost:8080/swagger-ui.html`

## Frontend Setup

1. Navigate to `frontend/` directory
2. Run `npm install`
3. Run `npm run dev`
4. Frontend runs on `http://localhost:5173`

The frontend automatically connects to `http://localhost:8080/api` in development.

## Running Tests

**Backend:**
```bash
mvn test
```

**Frontend:**
```bash
cd frontend
npm test
```

## Building for Production

**Backend:**
```bash
mvn clean package -DskipTests
```

**Frontend:**
```bash
cd frontend
npm run build
```

## Docker Deployment

1. Create `.env` file from `.env.example`
2. Update environment variables (especially JWT_SECRET)
3. Run `docker-compose up -d`

Services will be available at:
- Backend: `http://localhost:8080`
- Frontend: `http://localhost:3000`

## Project Structure

```
stream-chat/
├── src/
│   ├── main/java/com/streamchat/     # Backend code
│   │   ├── controller/                # REST API controllers
│   │   ├── service/                   # Business logic
│   │   ├── model/                     # JPA entities
│   │   └── config/                    # Spring configuration
│   └── test/                          # Backend tests
├── frontend/
│   ├── src/
│   │   ├── components/                # React components
│   │   ├── stores/                    # Zustand state management
│   │   ├── hooks/                     # Custom React hooks
│   │   ├── services/                  # API client
│   │   └── utils/                     # Utilities
│   └── public/                        # Static assets
└── docker-compose.yml                 # Docker Compose config
```

## Common Issues

**Backend won't start:**
- Make sure port 8080 is not in use
- Check that Java 17+ is installed (`java -version`)

**Frontend can't connect to backend:**
- Make sure backend is running on port 8080
- Check `.env` or environment variables for correct API URL

**Docker build fails:**
- Ensure Docker is running
- Run `docker system prune -a` to free up space if needed

## Deployment Notes

For production:
- Change JWT_SECRET to a strong random string (64+ chars)
- Set up PostgreSQL and Redis instead of using defaults
- Configure CORS_ALLOWED_ORIGINS for your domain
- Use HTTPS for all connections
- Enable SPRING_PROFILES_ACTIVE=prod in environment
