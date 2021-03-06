/*
 * Copyright 2016, Yahoo! Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 */
package com.yahoo.sketches.hive.quantiles;

import java.util.Arrays;

import org.apache.hadoop.hive.ql.exec.UDFArgumentException;
import org.apache.hadoop.hive.ql.exec.UDFArgumentTypeException;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFParameterInfo;
import org.apache.hadoop.hive.ql.udf.generic.SimpleGenericUDAFParameterInfo;
import org.apache.hadoop.hive.ql.udf.generic.GenericUDAFEvaluator.Mode;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.ObjectInspectorFactory;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector;
import org.apache.hadoop.hive.serde2.objectinspector.PrimitiveObjectInspector.PrimitiveCategory;
import org.apache.hadoop.hive.serde2.objectinspector.primitive.PrimitiveObjectInspectorFactory;
import org.apache.hadoop.io.BytesWritable;

import com.yahoo.sketches.memory.NativeMemory;
import com.yahoo.sketches.quantiles.DoublesSketch;

import org.testng.annotations.Test;
import org.testng.Assert;

public class UnionDoublesSketchUDAFTest {

  static final ObjectInspector binaryInspector =
    PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.BINARY);

  @Test(expectedExceptions = UDFArgumentException.class)
  public void getEvaluatorTooFewInspectors() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoublesSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentException.class)
  public void getEvaluatorTooManyInspectors() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector, binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoublesSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentTypeException.class)
  public void getEvaluatorWrongType() throws Exception {
    ObjectInspector intInspector =
      PrimitiveObjectInspectorFactory.getPrimitiveWritableObjectInspector(PrimitiveCategory.INT);

    ObjectInspector[] inspectors = new ObjectInspector[] { intInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoublesSketchUDAF().getEvaluator(info);
  }

  @Test(expectedExceptions = UDFArgumentTypeException.class)
  public void getEvaluatorWrongCategory() throws Exception {
    ObjectInspector structInspector = ObjectInspectorFactory.getStandardStructObjectInspector(
      Arrays.asList("a"),
      Arrays.asList(binaryInspector)
    );
    ObjectInspector[] inspectors = new ObjectInspector[] { structInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    new UnionDoublesSketchUDAF().getEvaluator(info);
  }

  @Test
  public void iterateTerminatePartial() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionDoublesSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL1, inspectors);
    checkResultInspector(resultInspector);

    DoublesUnionState state = (DoublesUnionState) eval.getNewAggregationBuffer();
    state.init(256);
    state.update(1.0);

    DoublesSketch sketch = DoublesSketch.builder().setK(256).build();
    sketch.update(2.0);

    eval.iterate(state, new Object[] { new BytesWritable(sketch.toByteArray()) });

    BytesWritable bytes = (BytesWritable) eval.terminatePartial(state);
    DoublesSketch resultSketch = DoublesSketch.heapify(new NativeMemory(bytes.getBytes()));
    Assert.assertEquals(resultSketch.getK(), 256);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), 1.0);
    Assert.assertEquals(resultSketch.getMaxValue(), 2.0);
    eval.close();
  }

  @Test
  public void mergeTerminate() throws Exception {
    ObjectInspector[] inspectors = new ObjectInspector[] { binaryInspector };
    GenericUDAFParameterInfo info = new SimpleGenericUDAFParameterInfo(inspectors, false, false);
    GenericUDAFEvaluator eval = new UnionDoublesSketchUDAF().getEvaluator(info);
    ObjectInspector resultInspector = eval.init(Mode.PARTIAL2, inspectors);
    checkResultInspector(resultInspector);

    DoublesUnionState state = (DoublesUnionState) eval.getNewAggregationBuffer();
    state.init(256);
    state.update(1.0);

    DoublesSketch sketch = DoublesSketch.builder().setK(256).build();
    sketch.update(2.0);

    eval.merge(state, new BytesWritable(sketch.toByteArray()));

    BytesWritable bytes = (BytesWritable) eval.terminate(state);
    DoublesSketch resultSketch = DoublesSketch.heapify(new NativeMemory(bytes.getBytes()));
    Assert.assertEquals(resultSketch.getK(), 256);
    Assert.assertEquals(resultSketch.getRetainedItems(), 2);
    Assert.assertEquals(resultSketch.getMinValue(), 1.0);
    Assert.assertEquals(resultSketch.getMaxValue(), 2.0);
    eval.close();
  }

  private static void checkResultInspector(ObjectInspector resultInspector) {
    Assert.assertNotNull(resultInspector);
    Assert.assertEquals(resultInspector.getCategory(), ObjectInspector.Category.PRIMITIVE);
    Assert.assertEquals(
      ((PrimitiveObjectInspector) resultInspector).getPrimitiveCategory(),
      PrimitiveObjectInspector.PrimitiveCategory.BINARY
    );
  }

}
