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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import kotlin.Pair;

public class KotlinStateUtil {
  private static Logger logger = LoggerFactory.getLogger(KotlinStateUtil.class);
  private static final Set<Method> objectMethods =
      new HashSet<>(Arrays.asList(Object.class.getMethods()));

  public static void updateVars(
      Map<String, KotlinVariableInfo> vars,
      AggregatedReplStageState<?, ?> state) {
    try {
      Object script = getScript(state);
      getNewFields(script, vars);
    } catch (ReflectiveOperationException | NullPointerException e) {
      logger.error("Exception updating current variables", e);
    }
  }

  public static void updateMethods(
      Set<Method> methods,
      AggregatedReplStageState<?, ?> state) {
    try {
      Object script = getScript(state);
      getNewMethods(script, methods);
    } catch (ReflectiveOperationException | NullPointerException e) {
      logger.error("Exception updating current methods", e);
    }
  }

  private static Object getScript(AggregatedReplStageState<?, ?> state)
      throws NullPointerException {
    Object script;
    Object statePair = Objects.requireNonNull(state.getHistory().peek())
        .getItem()
        .getSecond();
    script = ((Pair<?, ?>) statePair).getSecond();
    return script;
  }

  private static void getNewFields(
      Object script,
      Map<String, KotlinVariableInfo> vars) throws ReflectiveOperationException {

    List<Object> classesToVisit = new ArrayList<>();
    classesToVisit.add(script);
    classesToVisit.add(getImplicitReceiver(script));

    for (Object o : classesToVisit) {
      Field[] fields = o.getClass().getDeclaredFields();

      for (Field field : fields) {
        String fieldName = field.getName();
        if (fieldName.contains("$$implicitReceiver") || fieldName.contains("kotlinVars")) {
          continue;
        }

        field.setAccessible(true);
        Object value = field.get(o);
        if (!fieldName.contains("script$")) {
          vars.put(fieldName, new KotlinVariableInfo(fieldName, value, field));
        }
      }
    }
  }

  private static void getNewMethods(
      Object script,
      Set<Method> methods) throws ReflectiveOperationException {
    Set<Method> newMethods = new HashSet<>(Arrays.asList(
        script.getClass().getMethods()));
    newMethods.removeAll(objectMethods);
    methods.addAll(newMethods);
  }


  private static Object getImplicitReceiver(Object script)
      throws ReflectiveOperationException {
    Field receiverField = script.getClass().getDeclaredField("$$implicitReceiver0");
    return receiverField.get(script);
  }
}
