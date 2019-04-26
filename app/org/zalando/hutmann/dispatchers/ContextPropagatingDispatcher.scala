/*
Portions under The MIT License (MIT)

Copyright (c) 2016 Zalando SE

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

combined on 06/04/2017 with portions from

https://github.com/jroper/thread-local-context-propagation/blob/master/app/dispatchers/ContextPropagatingDispatcher.scala

under the Apache 2 license, quoted below.

  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
  project except in compliance with the License. You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0.

  Unless required by applicable law or agreed to in writing, software distributed under the
  License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
  either express or implied. See the License for the specific language governing permissions
  and limitations under the License.
 */

package org.zalando.hutmann.dispatchers

import java.util.concurrent.TimeUnit

import akka.dispatch._
import com.typesafe.config.Config
import org.zalando.hutmann.logging.Context

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.{ Duration, FiniteDuration }

/**
  * Configurator for a context propagating dispatcher.
  */
class ContextPropagatingDispatcherConfigurator(config: Config, prerequisites: DispatcherPrerequisites)
  extends MessageDispatcherConfigurator(config, prerequisites) {

  private val instance = new ContextPropagatingDispatcher(
    this,
    config.getString("id"),
    config.getInt("throughput"),
    FiniteDuration(config.getDuration("throughput-deadline-time", TimeUnit.NANOSECONDS), TimeUnit.NANOSECONDS),
    configureExecutor(),
    FiniteDuration(config.getDuration("shutdown-timeout", TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS)
  )

  override def dispatcher(): MessageDispatcher = instance
}

/**
  * A context propagating dispatcher.
  *
  * This dispatcher propagates the current request context if it's set when it's executed.
  */
class ContextPropagatingDispatcher(
    _configurator:                  MessageDispatcherConfigurator,
    id:                             String,
    throughput:                     Int,
    throughputDeadlineTime:         Duration,
    executorServiceFactoryProvider: ExecutorServiceFactoryProvider,
    shutdownTimeout:                FiniteDuration
) extends Dispatcher(
  _configurator, id, throughput, throughputDeadlineTime, executorServiceFactoryProvider, shutdownTimeout
) { self =>

  override def prepare(): ExecutionContext = new ExecutionContext {
    val context = Context.getContext
    def execute(r: Runnable): Unit = self.execute(new Runnable {
      def run(): Unit = Context.withContext(context)(r.run())
    })
    def reportFailure(t: Throwable): Unit = self.reportFailure(t)
  }
}
