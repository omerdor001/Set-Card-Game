# Set Card Game – SPL Assignment 2

This project is an implementation of a simplified version of the **Set Card Game**, developed as part of **SPL Assignment 2**.  
The assignment focuses on **Java concurrency, synchronization, and testing**, giving practice with multi-threaded programming, proper synchronization, and writing unit tests.

All **UI, graphics, keyboard input, and window management** are provided.  
Our task is to implement the **game logic**, including players, dealer, table management, and concurrency handling.

---

## 🎮 Game Description

**Set** is a card game played with a deck of 81 unique cards.  
Each card has **four features**, each with **three possible values**:

- **Color**: red, green, purple  
- **Number**: 1, 2, 3  
- **Shape**: squiggle, diamond, oval  
- **Shading**: solid, partial, empty  

A **legal set** is a group of 3 cards such that for **each feature**, the values are either **all the same** or **all different**.

### Example:
- ✅ Legal set: All shadings the same, but numbers, colors, and shapes all different.  
- ❌ Not a set: Two cards have the same shading, while the third differs.

---

## 🕹️ Game Flow

- The game starts with **12 cards** placed in a 3×4 grid on the table.  
- **Players (human or AI)** try to find sets by placing tokens on 3 cards.  
- Once a player selects 3 cards:
  - The **dealer** checks if it’s a valid set.  
  - ✅ Correct → player gains a point, cards are replaced, short freeze.  
  - ❌ Incorrect → player is penalized with a longer freeze.  
- If no legal sets exist, the dealer reshuffles the table.  
- The game ends when no legal sets remain in the deck or on the table.  
- The player with the most points wins!

---

## ⚙️ Concurrency & Synchronization

- **Dealer**: A single thread managing the game:
  - Deals/shuffles cards  
  - Checks sets in **FIFO order** for fairness  
  - Awards points/penalties  
  - Tracks the timer and reshuffles if needed  

- **Players**: Each player runs in its own thread:
  - Maintains a **bounded queue** of actions (size = 3)  
  - Places/removes tokens based on key presses  
  - Waits when frozen by the dealer  

- **Human Players**: Input handled via the keyboard.  
- **AI Players**: Simulated threads that randomly generate key presses.

---

## 🖥️ User Interface

- GUI provided via `UserInterface` and `UserInterfaceImpl`.  
- Keyboard layout mirrors the **3×4 grid** of the table.  
- Dealer and player states are updated visually.  
- Input is mapped automatically via `InputManager`.  

---

## 📂 Project Structure

Set-Card-Game/
│── pom.xml
│── src/
│ ├── main/java/bguspl/set/ex/ # Implementation (Dealer, Player, Table, etc.)
│ ├── test/java/bguspl/set/ex/ # Unit tests
│── config.properties # Game configuration


### Key Classes:
- **Dealer** – main game controller (thread)  
- **Player** – represents human or AI players (thread)  
- **Table** – holds cards and tokens  
- **Util** – helper methods for card/set validation  
- **Env, Config** – environment and configuration  

---

## 🧪 Unit Testing

- **JUnit 5** is used for testing.  
- **Mockito** can be used for mocking dependencies.  
- Requirement: at least **2 unit tests per class** for `Table`, `Dealer`, and `Player`.  

Example test files provided:
- `TableTest.java` (JUnit example)  
- `PlayerTest.java` (Mockito example)  

Run tests with:

```bash
mvn clean test

##  🚀 Build & Run

The project uses Maven as the build tool.

### Compile & Run:

mvn clean compile
java -cp target/classes bguspl.set.Main

### Run Tests:

mvn test



