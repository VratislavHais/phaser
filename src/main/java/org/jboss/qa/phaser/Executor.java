/*
 * Copyright 2015 Red Hat Inc. and/or its affiliates and other contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.qa.phaser;

import org.jboss.qa.phaser.context.Context;
import org.jboss.qa.phaser.context.PropertyAnnotationProcessor;
import org.jboss.qa.phaser.processors.CdiExecutor;
import org.jboss.qa.phaser.registry.InjectAnnotationProcessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Executor {

	private List<Object> jobs;
	private List<ExecutionNode> roots;
	private org.jboss.qa.phaser.registry.InstanceRegistry register;

	public Executor(List<Object> jobs, List<ExecutionNode> roots, org.jboss.qa.phaser.registry.InstanceRegistry register) throws Exception {
		this.jobs = jobs;
		this.roots = roots;
		this.register = register;
		injectFields();
	}

	public void execute() throws Exception {
		final List<ErrorReport> throwAtEnd = new LinkedList<>();
		invokeJobMethods(BeforeJob.class);

		final Queue<ExecutionNode> nodeQueue = new LinkedList<>(roots);
		boolean finalizeState = false;
		while (!nodeQueue.isEmpty()) {
			final ExecutionNode node = nodeQueue.poll();

			final ExecutionError err = node.execute(finalizeState, register);

			if (err != null) {
				final ExceptionHandling eh = err.getExceptionHandling();
				final ErrorReport errorReport = new ErrorReport("Exception thrown by phase execution:", err.getThrowable());
				switch (eh.getReport()) {
					case THROW_AT_END:
						throwAtEnd.add(errorReport);
						break;
					case LOG:
						ErrorReporter.report(errorReport);
						break;
					default:
						log.debug("Exception by phase execution, continue.");
				}

				if (eh.getExecution() == ExceptionHandling.Execution.IMMEDIATELY_STOP) {
					break;
				} else if (eh.getExecution() == ExceptionHandling.Execution.FINALIZE) {
					finalizeState = true;
				}
			}
			nodeQueue.addAll(node.getChildNodes());
		}

		invokeJobMethods(AfterJob.class);
		ErrorReporter.finalErrorReport(throwAtEnd);
	}

	private void invokeJobMethods(Class<? extends Annotation> annotationClass) throws Exception {
		for (Object job : jobs) {
			for (Method m : job.getClass().getMethods()) {
				final Annotation annotation = m.getAnnotation(annotationClass);
				if (annotation != null) {
					m.invoke(job);
				}
			}
		}
	}

	private void injectFields() throws Exception {
		final CdiExecutor.CdiExecutorBuilder cdiExecutorBuilder = CdiExecutor.builder();

		final List<Context> ctxs = register.get(Context.class);
		if (!ctxs.isEmpty()) {
			cdiExecutorBuilder.processor(new PropertyAnnotationProcessor(ctxs.get(0)));
		} else {
			log.warn("Property injection is not activated. You can activate it by adding context into instance registry");
		}
		final CdiExecutor cdiExecutor = cdiExecutorBuilder.processor(new InjectAnnotationProcessor(register)).build();

		for (final Object job : jobs) {
			cdiExecutor.inject(job);
		}
	}
}
