/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zeppelin.kotlin.reflect;

import org.jetbrains.kotlin.cli.common.repl.AggregatedReplStageState;
import org.jetbrains.kotlin.cli.common.repl.ReplHistoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kotlin.Pair;
import kotlin.reflect.KFunction;
import kotlin.reflect.KProperty;
import kotlin.reflect.jvm.ReflectJvmMapping;

/**
 * ContextUpdater updates current user-defined functions and variables
 * to use in completion and KotlinContext.
 */
public class ContextUpdater {
  private final Logger logger = LoggerFactory.getLogger(ContextUpdater.class);
  private final Set<Method> objectMethods =
      new HashSet<>(Arrays.asList(Object.class.getMethods()));
  
  private AggregatedReplStageState<?, ?> state;
  private Map<String, KotlinVariableInfo> vars;
  private Set<KotlinFunctionInfo> functions;

  public ContextUpdater(AggregatedReplStageState<?, ?> state,
                        Map<String, KotlinVariableInfo> vars, 
                        Set<KotlinFunctionInfo> functions) {
    this.state = state;
    this.vars = vars;
    this.functions = functions;
  }

  public void update() {
    try {
      List<Object> lines = getLines();
      refreshVariables(lines);
      refreshMethods(lines);
    } catch (ReflectiveOperationException | NullPointerException e) {
      logger.error("Exception updating current variables", e);
    }
  }

  private void refreshMethods(List<Object> lines) {
    functions.clear();
    for (Object line : lines) {
      Method[] methods = line.getClass().getMethods();
      for (Method method : methods) {
        if (objectMethods.contains(method) || method.getName().equals("main")) {
          continue;
        }
        KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
        if (function == null) {
          continue;
        }
        functions.add(new KotlinFunctionInfo(function));
      }
    }
  }

  private List<Object> getLines() {
    List<Object> lines = state.getHistory().stream()
        .map(this::getLineFromRecord)
        .collect(Collectors.toList());

    Collections.reverse(lines);
    return lines;
  }

  private Object getLineFromRecord(ReplHistoryRecord<? extends Pair<?, ?>> record) {
    Object statePair = record.getItem().getSecond();
    return ((Pair<?, ?>) statePair).getSecond();
  }

  private Object getImplicitReceiver(Object script)
      throws ReflectiveOperationException {
    Field receiverField = script.getClass().getDeclaredField("$$implicitReceiver0");
    return receiverField.get(script);
  }

  private void refreshVariables(List<Object> lines) throws ReflectiveOperationException {
    vars.clear();
    if (!lines.isEmpty()) {
      Object receiver = getImplicitReceiver(lines.get(0));
      findReceiverVariables(receiver);
    }
    for (Object line : lines) {
      findLineVariables(line);
    }
  }

  // For lines, we only want fields from top level class
  private void findLineVariables(Object line) throws IllegalAccessException {
    Field[] fields = line.getClass().getDeclaredFields();
    findVariables(fields, line);
  }

  // For implicit receiver, we want to also get fields in parent classes
  private void findReceiverVariables(Object receiver) throws IllegalAccessException {
    List<Field> fieldsList = new ArrayList<>();
    for (Class<?> cl = receiver.getClass(); cl != null; cl = cl.getSuperclass()) {
      fieldsList.addAll(Arrays.asList(cl.getDeclaredFields()));
    }
    findVariables(fieldsList.toArray(new Field[0]), receiver);
  }

  private void findVariables(Field[] fields, Object o) throws IllegalAccessException {
    for (Field field : fields) {
      String fieldName = field.getName();
      if (fieldName.contains("$$implicitReceiver")) {
        continue;
      }

      field.setAccessible(true);
      Object value = field.get(o);
      if (!fieldName.contains("script$")) {
        KProperty<?> descriptor = ReflectJvmMapping.getKotlinProperty(field);
        if (descriptor != null) {
          vars.putIfAbsent(fieldName, new KotlinVariableInfo(value, descriptor));
        }
      }
    }
  }
}
