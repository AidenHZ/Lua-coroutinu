# Lua风格的协程实现

本项目是一个基于 Kotlin 的 Lua 风格协程的实现。Lua 协程是非对称的，有栈的协程。通过该项目，演示了如何在 Kotlin 中实现类似于 Lua 的协程模型。

## 功能描述

本项目实现了具有如下特性的 Lua 风格协程：

- **非对称协程**：与 Lua 协程类似，生产者和消费者协程通过 `yield` 和 `resume` 实现协程间的交互。
- **协程栈**：协程状态在 `resume` 和 `yield` 之间保存和流转，类似于 Lua 的协程机制。
- **线程安全**：通过 `AtomicReference` 来确保协程状态在多线程情况下的安全性。

## 使用方式

### 生产者和消费者

在 `main` 协程中，创建了两个协程：

- **生产者协程**：发送一系列整数，并使用 `yield` 暂停协程，将值传递给消费者。
- **消费者协程**：接收生产者传递的值，并进行处理和输出。

```kotlin
suspend fun main() {
    val producer = Coroutine.create<Unit, Int>(Dispatcher()) {
        for (i in 0..3) {
            log("send", i)
            yield(i)
        }
        200
    }

    val consumer = Coroutine.create<Int, Unit>(Dispatcher()) { param: Int ->
        log("start", param)
        for (i in 0..3) {
            val value = yield(Unit)
            log("receive", value)
        }
    }

    while (producer.isActive && consumer.isActive) {
        val result = producer.resume(Unit)
        consumer.resume(result)
    }
}
```
### 核心类与模块

#### 1. **`Coroutine<P, R>`**

`Coroutine` 类是 Lua 风格协程的核心实现，包含以下关键功能：

- 协程的状态流转（`Created`、`Yielded`、`Resumed` 和 `Dead`）。
- 使用 `suspendCoroutine` 实现协程挂起与恢复。
- `resume` 和 `yield` 的协同工作机制。

#### 2. **`Dispatcher` 和 `DispatcherContinuation`**

`Dispatcher` 是协程的调度器，允许通过单线程执行器来调度协程的恢复操作。`DispatcherContinuation` 类实现了协程的拦截器，用来在恢复时调度协程。

#### 3. **`Status`**

`Status` 是协程的状态类，记录了协程的生命周期，包括 `Created`、`Yielded`、`Resumed` 和 `Dead` 状态。

## 代码结构

- `Coroutine<P, R>`：协程的主类，实现了非对称协程的核心逻辑。
- `Dispatcher`：协程调度器，负责调度协程的执行。
- `DispatcherContinuation`：协程的 `Continuation` 拦截器，负责在线程池中恢复协程。
- `Status`：封装协程的状态，用来控制协程的执行流。

## 如何运行

1. 确保你已经安装了 JDK 1.8 或更高版本，以及配置好 Kotlin 开发环境。
    
2. 克隆此项目到本地：

    ```bash
    git clone https://github.com/你的用户名/项目名.git
    ```

3. 在项目根目录下使用 `gradlew build` 构建项目，或在 Kotlin 支持的 IDE（如 IntelliJ IDEA）中打开并运行项目。

4. 运行 `main` 函数，你将看到生产者和消费者协程之间的数据流动。

## 示例输出

```bash
send 0
start 0
receive 0
send 1
receive 1
send 2
receive 2
send 3
receive 3
