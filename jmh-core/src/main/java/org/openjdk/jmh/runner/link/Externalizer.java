/*
 * Copyright (c) 2014, 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.openjdk.jmh.runner.link;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.BenchmarkParams;
import org.openjdk.jmh.infra.IterationParams;
import org.openjdk.jmh.results.IterationResult;
import org.openjdk.jmh.results.IterationResultMetaData;
import org.openjdk.jmh.results.Result;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collection;

final class Externalizer
{
    static void write(Object obj, ObjectOutput out)
    {
        try
        {
            out.writeUTF(obj.getClass().getName());
            if (obj instanceof OutputFormatFrame)
            {
                writeOutputFormatFrame(obj, out);
            }
            else if (obj instanceof InfraFrame)
            {
                // no-op
            }
            else if (obj instanceof HandshakeInitFrame)
            {
                out.writeLong(((HandshakeInitFrame) obj).getPid());
            }
            else if (obj instanceof ResultsFrame)
            {
                write(((ResultsFrame) obj).getRes(), out);
            }
            else if (obj instanceof IterationResult)
            {
                writeIterationResult(obj, out);
            }
            else if (obj instanceof BenchmarkParams)
            {
                writeBenchmarkParams(obj, out);
            }
            // TODO IterationParams
            // TODO IterationResultMetaData
            // TODO Collection...
            // TODO Multimap....
            else if (obj instanceof String)
            {
                out.writeUTF((String) obj);
            }
            else
            {
                throw new RuntimeException("Unknown type at write: " + obj.getClass());
            }
//            else if (obj instanceof Long)
//            {
//                out.writeLong((Long) obj);
//            }
//            else if (obj instanceof Integer)
//            {
//                out.writeInt((Integer) obj);
//            }
//            else if (obj instanceof Short)
//            {
//                out.writeShort((short) obj);
//            }
//            else if (obj instanceof Byte)
//            {
//                out.writeByte((byte) obj);
//            }
//            else if (obj instanceof )
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static void writeBenchmarkParams(Object obj, ObjectOutput out) throws IOException
    {
        final BenchmarkParams params = (BenchmarkParams) obj;
        out.writeUTF(params.getBenchmark());
        out.writeUTF(params.generatedBenchmark());
        out.writeBoolean(params.shouldSynchIterations());
        out.writeInt(params.getThreads());

        out.writeInt(params.getThreadGroups().length);
        for (int threadGroup : params.getThreadGroups())
        {
            out.writeInt(threadGroup);
        }

        write(params.getThreadGroupLabels(), out);
        out.writeInt(params.getForks());
        out.writeInt(params.getWarmupForks());
        write(params.getWarmup(), out);
        write(params.getMeasurement(), out);
        ... // TODO
    }

    private static void writeIterationResult(Object obj, ObjectOutput out)
    {
        final IterationResult result = (IterationResult) obj;
        write(result.getBenchmarkParams(), out);
        write(result.getParams(), out);
        write(result.getMetadata(), out);
        write(result.getRawPrimaryResults(), out);
        write(result.getSecondaryResults(), out);
    }

    private static void writeOutputFormatFrame(Object obj, ObjectOutput out) throws IOException
    {
        final OutputFormatFrame frame = (OutputFormatFrame) obj;
        out.writeUTF(frame.method);
        out.writeInt(frame.args.length);
        for (Object arg : frame.args)
        {
            write(arg, out);
        }
    }

    static <T> T read(ObjectInput in)
    {
        try
        {
            final String className = in.readUTF();
            switch (className)
            {
                case "org.openjdk.jmh.runner.link.OutputFormatFrame":
                    return readOutputFormatFrame(in);
                case "org.openjdk.jmh.runner.link.InfraFrame":
                    return (T) new InfraFrame(InfraFrame.Type.ACTION_PLAN_REQUEST);
                case "org.openjdk.jmh.runner.link.HandshakeInitFrame":
                    return (T) new HandshakeInitFrame(in.readLong());
                case "org.openjdk.jmh.runner.link.ResultsFrame":
                    return (T) new ResultsFrame((IterationResult) read(in));
                case "org.openjdk.jmh.results.IterationResult":
                    return readIterationResult(in);
                case "java.lang.String":
                    return (T) in.readUTF();
                default:
                    throw new RuntimeException("Unknown type at read: " + className);
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static <T> T readIterationResult(ObjectInput in)
    {
        final IterationResult result = new IterationResult(
            Externalizer.<BenchmarkParams>read(in)
            , Externalizer.<IterationParams>read(in)
            , Externalizer.<IterationResultMetaData>read(in)
        );

        result.addResults(Externalizer.<Collection<? extends Result>>read(in));
        result.addResults(Externalizer.<Collection<? extends Result>>read(in));

        return (T) result;
    }

    private static <T> T readOutputFormatFrame(ObjectInput in) throws IOException
    {
        final String method = in.readUTF();
        final int length = in.readInt();
        final Object[] args = new Object[length];
        for (int i = 0; i < length; i++)
        {
            args[i] = read(in);
        }
        return (T) new OutputFormatFrame(method, args);
    }
}
