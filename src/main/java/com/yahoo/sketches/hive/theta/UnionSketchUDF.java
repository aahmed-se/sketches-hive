/*******************************************************************************
 * Copyright 2015, Yahoo Inc.
 * Licensed under the terms of the Apache License 2.0. See LICENSE file at the project root for terms.
 *******************************************************************************/
package com.yahoo.sketches.hive.theta;

import org.apache.hadoop.hive.ql.exec.UDF;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;

import com.yahoo.sketches.memory.NativeMemory;
import com.yahoo.sketches.theta.SetOperation;
import com.yahoo.sketches.theta.Union;

/**
 * Hive union sketch UDF.
 *
 */
public class UnionSketchUDF extends UDF {
  public static final int DEFAULT_SIZE = 16384;

  private static final int EMPTY_SKETCH_SIZE_BYTES = 8;

  /**
   * Main logic called by hive if sketchSize is also passed in. Union two
   * sketches of same or different column.
   * 
   * @param firstSketch
   *          first sketch to be unioned.
   * @param secondSketch
   *          second sketch to be unioned.
   * @param sketchSize
   *          final output unioned sketch size.
   *          This must be a power of 2 and larger than 16. If zero, DEFAULT is used.
   * @return resulting sketch of union.
   */
  public BytesWritable evaluate(final BytesWritable firstSketch, final BytesWritable secondSketch, final IntWritable sketchSize) {
    
    final int sketch_size = (sketchSize != null)? sketchSize.get() : DEFAULT_SIZE;

    final Union union = SetOperation.builder().buildUnion(sketch_size);

    // update union with first sketch, if null do nothing
    if ((firstSketch != null) && (firstSketch.getLength() >= EMPTY_SKETCH_SIZE_BYTES)) {
      union.update(new NativeMemory(firstSketch.getBytes()));
    }

    // update union second sketch, if null do nothing
    if ((secondSketch != null) && (secondSketch.getLength() >= EMPTY_SKETCH_SIZE_BYTES)) {
      union.update(new NativeMemory(secondSketch.getBytes()));
    }

    byte[] resultSketch = union.getResult(false, null).toByteArray(); //unordered CompactSketch
    final BytesWritable result = new BytesWritable();
    result.set(resultSketch, 0, resultSketch.length);
    return result;
  }

  /**
   * Main logic called by hive if sketchSize is not passed in. Union two
   * sketches of same or different column.
   * 
   * @param firstSketch
   *          first sketch to be unioned.
   * @param secondSketch
   *          second sketch to be unioned.
   * @return resulting sketch of union.
   */
  public BytesWritable evaluate(final BytesWritable firstSketch, final BytesWritable secondSketch) {
    return evaluate(firstSketch, secondSketch, null);
  }

}
