# 💰 Expensive Tracker

> Personal expense tracking application for managing daily expenses, monitoring budgets, and visualizing spending patterns.

## 📋 Overview

Expensive Tracker is a Java-based expense management application designed to help users take control of their finances. Whether you're tracking daily coffee purchases or monitoring major budget categories, this application provides an intuitive way to manage, analyze, and visualize your spending habits.

## ✨ Features

- **Expense Logging**: Easily record daily expenses with categories and descriptions
- **Budget Monitoring**: Set and track budgets across different spending categories
- **Spending Analytics**: Visualize your spending patterns with charts and reports
- **Category Management**: Organize expenses into customizable categories
- **Expense History**: Maintain a complete record of all transactions
- **Budget Alerts**: Get notifications when approaching budget limits

## 🛠️ Technology Stack

- **Language**: Java
- **Build Tool**: Maven/Gradle (specify as applicable)
- **Architecture**: Object-Oriented Design

## 🚀 Getting Started

### Prerequisites

- Java 11 or higher
- Maven or Gradle (depending on the build tool used)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/JKPrasad01/Expensive-Tracker.git
cd Expensive-Tracker
```

2. Build the project:
```bash
# Using Maven
mvn clean install

# Or using Gradle
gradle build
```

3. Run the application:
```bash
# Using Maven
mvn spring-boot:run

# Or using Gradle
gradle bootRun

# Or directly
java -jar target/expensive-tracker.jar
```

## 📖 Usage

1. **Add Expense**: Click "Add Expense" and enter the amount, category, and description
2. **View Budget**: Check your budget status in the Dashboard
3. **Generate Reports**: View spending patterns through visual charts and reports
4. **Manage Categories**: Customize expense categories based on your needs

## 📁 Project Structure

```
Expensive-Tracker/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/expensetracker/
│   │   │       ├── model/
│   │   │       ├── service/
│   │   │       ├── controller/
│   │   │       └── App.java
│   │   └── resources/
│   └── test/
├── pom.xml (or build.gradle)
└── README.md
```

## 🔧 Configuration

Create a `config.properties` file in the resources folder to configure:
- Database connection settings
- Default currency
- Budget notification thresholds

Example:
```properties
db.url=jdbc:mysql://localhost:3306/expensedb
db.user=root
db.password=password
app.currency=USD
```

## 📊 API Endpoints (if applicable)

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/expenses` | Get all expenses |
| POST | `/api/expenses` | Add new expense |
| GET | `/api/budget` | Get budget status |
| POST | `/api/budget` | Set budget limits |
| GET | `/api/reports` | Generate spending reports |

## 🧪 Testing

Run the test suite:
```bash
# Using Maven
mvn test

# Using Gradle
gradle test
```

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📝 License

This project is currently unlicensed. Please add appropriate license information when ready.

## 🐛 Issues & Support

Found a bug or have a suggestion? Please [open an issue](https://github.com/JKPrasad01/Expensive-Tracker/issues) on GitHub.

## 📞 Contact

For questions or feedback, reach out via:
- GitHub Issues: [JKPrasad01/Expensive-Tracker/issues](https://github.com/JKPrasad01/Expensive-Tracker/issues)
- GitHub Profile: [@JKPrasad01](https://github.com/JKPrasad01)

---

**Happy Tracking! 📊** Keep your expenses in check and your finances on track.
