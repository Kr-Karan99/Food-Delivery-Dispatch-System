# DeliverySystem - Scalable Food Delivery Platform

A modern, event-driven microservices-based delivery system built with **Spring Boot 3.2.5**, **Java 21**, and cloud-native technologies. This system efficiently manages orders, drivers, and real-time dispatch operations with high concurrency and scalability.

## 🚀 Features

- **Order Management Service**: Create, track, and manage delivery orders
- **Driver Management Service**: Manage driver profiles, availability, and status
- **Dispatch Service**: Intelligent order-to-driver assignment with real-time updates
- **Real-time Updates**: WebSocket support for live delivery tracking
- **Event-Driven Architecture**: Kafka-based asynchronous event processing
- **Distributed Caching**: Redis for geo-location queries and lock management
- **Database Persistence**: PostgreSQL for reliable data storage
- **Security**: Spring Security integration for authentication and authorization
- **Data Validation**: Input validation and error handling

## 🛠️ Technology Stack

| Component | Technology | Version |
|-----------|-----------|---------|
| **Framework** | Spring Boot | 3.2.5 |
| **Language** | Java | 21 |
| **Database** | PostgreSQL | Latest |
| **Cache** | Redis | Latest |
| **Message Broker** | Apache Kafka | Latest |
| **Build Tool** | Maven | 3.x |
| **WebSocket** | Spring WebSocket | Included in Spring Boot |

## 📋 Prerequisites

Before you begin, ensure you have the following installed:

- **Java 21 JDK** - [Download](https://www.oracle.com/java/technologies/downloads/#java21)
- **Apache Maven 3.9+** - [Download](https://maven.apache.org/download.cgi) _(Optional - project includes Maven wrapper)_
- **Docker & Docker Compose** - [Download](https://www.docker.com/products/docker-desktop)
- **PostgreSQL** - [Download](https://www.postgresql.org/download/) _(Optional if using Docker)_
- **Redis** - [Download](https://redis.io/download) _(Optional if using Docker)_
- **Apache Kafka** - [Download](https://kafka.apache.org/downloads) _(Optional if using Docker)_
- **Git** - [Download](https://git-scm.com/downloads)

## ⚡ Quick Start

### Option 1: Using Docker Compose (Recommended)

The easiest way to get the entire stack running:

```bash
# Clone the repository
git clone <your-repo-url>
cd DeliverySystem

# Start all services (PostgreSQL, Redis, Kafka, Zookeeper)
docker-compose up -d

# Build the application
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

The application will be available at `http://localhost:8080`

### Option 2: Manual Setup

If you prefer to set up services manually:

```bash
# 1. Ensure PostgreSQL is running on localhost:5432
# 2. Ensure Redis is running on localhost:6379
# 3. Ensure Kafka is running on localhost:9092

# Build the project
./mvnw clean package

# Run the application
./mvnw spring-boot:run
```

## 🗂️ Project Structure

```
DeliverySystem/
├── src/main/java/com/delivery/system/
│   ├── DeliverySystemApplication.java          # Application entry point
│   ├── config/                                  # Configuration classes
│   │   ├── KafkaConfig.java                    # Kafka producer/consumer setup
│   │   ├── RedisConfig.java                    # Redis configuration
│   │   ├── SecurityConfig.java                 # Spring Security configuration
│   │   └── WebSocketConfig.java                # WebSocket configuration
│   ├── controller/                              # REST API endpoints
│   │   ├── OrderController.java                # Order API
│   │   └── DriverController.java               # Driver API
│   ├── service/impl/                            # Business logic
│   │   ├── OrderService.java                   # Order operations
│   │   ├── DriverService.java                  # Driver operations
│   │   └── DispatchService.java                # Dispatch logic
│   ├── entity/                                  # JPA entities
│   │   ├── Order.java                          # Order entity
│   │   └── Driver.java                         # Driver entity
│   ├── dto/                                     # Data transfer objects
│   │   ├── OrderDTO.java                       # Order DTO
│   │   └── DriverDTO.java                      # Driver DTO
│   ├── event/                                   # Event classes
│   │   ├── OrderCreatedEvent.java              # Order creation event
│   │   └── DriverAssignedEvent.java            # Driver assignment event
│   ├── repository/                              # Data access layer
│   │   ├── OrderRepository.java                # Order DB operations
│   │   └── DriverRepository.java               # Driver DB operations
│   ├── enums/                                   # Enum classes
│   │   ├── OrderStatus.java                    # Order statuses
│   │   └── DriverStatus.java                   # Driver statuses
│   └── exception/                               # Exception handling
├── src/main/resources/
│   ├── application.properties                  # Application configuration
│   └── static/ & templates/                    # Static files & templates
├── src/test/                                    # Test files
├── pom.xml                                      # Maven configuration
├── docker-compose.yml                          # Docker services
└── README.md                                    # This file
```

## 🔌 Database Configuration

Update `src/main/resources/application.properties` with your database credentials:

```properties
# PostgreSQL Database
spring.datasource.url=jdbc:postgresql://localhost:5432/food_delivery_db
spring.datasource.username=postgres
spring.datasource.password=your_password
```

## 🔴 Redis Configuration

Configure Redis connection:

```properties
# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

## 📨 Kafka Configuration

Configure Kafka broker:

```properties
# Kafka
spring.kafka.bootstrap-servers=localhost:9092
```

## 📚 API Endpoints

### Orders
- `POST /api/orders` - Create a new order
- `GET /api/orders/{id}` - Get order details
- `GET /api/orders` - List all orders
- `PUT /api/orders/{id}` - Update order status

### Drivers
- `POST /api/drivers` - Register a new driver
- `GET /api/drivers/{id}` - Get driver details
- `GET /api/drivers` - List all drivers
- `PUT /api/drivers/{id}/status` - Update driver status

### Dispatch
- `POST /api/dispatch/assign` - Assign order to driver
- `GET /api/dispatch/{orderId}/status` - Get assignment status

## 🔐 Security

The application uses Spring Security. Default configuration includes:
- Basic authentication
- Password encoding (BCrypt)
- CORS configuration
- CSRF protection

Update security credentials in `SecurityConfig.java` for production use.

## 🧪 Testing

Run unit and integration tests:

```bash
./mvnw test
```

## 📦 Building for Production

Create an optimized JAR package:

```bash
./mvnw clean package -DskipTests
```

The JAR file will be generated at `target/delivery-system-0.0.1-SNAPSHOT.jar`

## 🐳 Docker Deployment

Build Docker image:

```bash
docker build -t delivery-system:latest .
```

Run container:

```bash
docker run -p 8080:8080 delivery-system:latest
```

## 📊 Monitoring & Logging

The application logs all operations and can be monitored via:
- Console logs (development)
- ELK Stack / Datadog (production recommended)
- Spring Actuator endpoints (if enabled)

## 🐛 Troubleshooting

### Connection Issues
- Ensure PostgreSQL is running on port 5432
- Ensure Redis is running on port 6379
- Ensure Kafka is running on port 9092
- Check firewall rules

### Port Already in Use
```bash
# Change port in application.properties
server.port=8081
```

### Database Schema Issues
```bash
# Reset database (⚠️ Warning: Deletes all data)
# Set in application.properties: spring.jpa.hibernate.ddl-auto=create
```

## 📝 Environment Variables

For production, use environment variables instead of hardcoding credentials:

```bash
export DATABASE_URL=jdbc:postgresql://your-host:5432/db
export DATABASE_USER=username
export DATABASE_PASSWORD=password
export REDIS_HOST=your-redis-host
export KAFKA_BROKER=your-kafka-host:9092
```

## 🔗 Related Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md) - System architecture details
- [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md) - Development guidelines
- [DEPLOYMENT_CHECKLIST.md](./DEPLOYMENT_CHECKLIST.md) - Production deployment steps
- [UNIT_TESTING_GUIDE.md](./UNIT_TESTING_GUIDE.md) - Testing best practices

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.

## 👥 Authors

- **Your Name** - Initial work and development

## 📞 Support

For issues, questions, or suggestions, please open an issue on GitHub or contact the development team.

---

**Last Updated**: April 2026
**Version**: 0.0.1-SNAPSHOT
