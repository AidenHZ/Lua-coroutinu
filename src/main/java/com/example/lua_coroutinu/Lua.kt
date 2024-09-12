package com.example.lua_coroutinu
import com.example.lua_coroutinu.utils.log
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.createCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class Lua {

}
/*
这是一个模仿Lua语言实现Coroutine
Lua是有栈的非对称协程。
实现效果：
suspend fun main() {
    val producer = Coroutine.create<Unit, Int>(Dispatcher()) {
        for(i in 0..3){
            log("send", i)
            yield(i)
        }
        200
    }

    val consumer = Coroutine.create<Int, Unit>(Dispatcher()) { param: Int ->
        log("start", param)
        for(i in 0..3){
            val value = yield(Unit)
            log("receive", value)
        }
    }

    while (producer.isActive && consumer.isActive){
        val result = producer.resume(Unit)
        consumer.resume(result)
    }
}
 */
sealed class Status{
    class   Created(val  continuation: Continuation<Unit>):Status()
    class   Yielded<P>(val continuation: Continuation<P>):Status()
    class   Resumed<R>(val continuation: Continuation<R>):Status()
    //对于所有的协程来讲，Dead状态都表示执行完毕，所以用单例比较合适，减少内存消耗。
    object  Dead:Status()
}

class Coroutine<P,R>(
    override val context: CoroutineContext = EmptyCoroutineContext,
    private val block:suspend Coroutine<P,R>.CoroutineBody.(P)->R
):Continuation<R>{
    private val body = CoroutineBody()
    //AtomicReference是java提供的一个线程安全的类,在多线程下确保线程安全
    private val status:java.util.concurrent.atomic.AtomicReference<Status>
    val isActive:Boolean
        get() = status.get()!=Status.Dead
    init {
        val coroutineBlock:suspend CoroutineBody.()->R = {block(parameter!!)}
        val start = coroutineBlock.createCoroutine(body,this)
        status= java.util.concurrent.atomic.AtomicReference(Status.Created(start))
    }

    //使用伴生对象而不用顶级函数，因为会使用到Coroutine的内部。
    companion object{
        fun <P,R> create(
            context:CoroutineContext = EmptyCoroutineContext,
            block: suspend Coroutine<P,R>.CoroutineBody.(P)->R
        ): Coroutine<P,R>{

            return Coroutine(context,block)
        }
    }
    inner class  CoroutineBody{
        var parameter:  P? = null
        suspend fun yield(value: R):P = suspendCoroutine {continuation ->
            //getAndUpdate是AtomicReference提供的方法
            //通过lambda表达式，更新status的值，操作是原子的，保证多个线程正常访问。
            val previousStatus = status.getAndUpdate{
                when(it){
                    is Status.Created -> throw IllegalStateException("Never started!")
                    is Status.Resumed<*> -> Status.Yielded(continuation)
                    is Status.Yielded<*> ->throw IllegalStateException("Already yielded!")
                    Status.Dead ->throw IllegalStateException("Already dead!")
                }
            }
            //检查previousStatus状态是否为tatus.Resumed<R>，是的话，调用continuation?.resume(value)恢复协程
            (previousStatus as? Status.Resumed<R>)?.continuation?.resume(value)

        }

    }

    override fun resumeWith(result: Result<R>) {
        val previewStatus = status.getAndUpdate {
            when(it){
                is Status.Created -> throw IllegalStateException("Never started!")
                is Status.Yielded<*> -> throw IllegalStateException("Already yielded!")
                is Status.Resumed<*> -> Status.Dead
                Status.Dead -> throw IllegalStateException("Already dead!")
            }
        }
        (previewStatus as? Status.Resumed<R>)?.continuation?.resumeWith(result)

    }
    suspend fun resume(value: P):R = suspendCoroutine {continuation ->
        //status.getAndUpdate原子性地获取当前状态并更新状态，确保线程安全。
        val previousStatus = status.getAndUpdate {
            when(it){
                is Status.Created -> {
                    body.parameter = value
                    Status.Resumed(continuation)
                }
                is Status.Yielded<*> -> Status.Resumed(continuation)
                is Status.Resumed<*> -> throw IllegalStateException("Already resumed!")
                Status.Dead -> throw IllegalStateException("Already dead!")
            }

        }
        //上面it只是流转了状态，下面需要完成状态对应的事情。
        when(previousStatus){
            is Status.Created -> previousStatus.continuation.resume(Unit)
            is Status.Yielded<*> -> (previousStatus as Status.Yielded<P>).continuation.resume(value)
            else -> {}
        }
    }

}
class Dispatcher: ContinuationInterceptor {
    override val key = ContinuationInterceptor

    private val executor = Executors.newSingleThreadExecutor()

    override fun <T> interceptContinuation(continuation: Continuation<T>): Continuation<T> {
        return DispatcherContinuation(continuation, executor)
    }
}

class DispatcherContinuation<T>(val continuation: Continuation<T>, val executor: Executor): Continuation<T> by continuation {

    override fun resumeWith(result: Result<T>) {
        executor.execute {
            continuation.resumeWith(result)
        }
    }
}








suspend fun main() {
    val producer = Coroutine.create<Unit, Int>(Dispatcher()) {
        for(i in 0..3){
            log("send", i)
            yield(i)
        }
        200
    }

    val consumer = Coroutine.create<Int, Unit>(Dispatcher()) { param: Int ->
        log("start", param)
        for(i in 0..3){
            val value = yield(Unit)
            log("receive", value)
        }
    }

    while (producer.isActive && consumer.isActive){
        val result = producer.resume(Unit)
        consumer.resume(result)
    }
}