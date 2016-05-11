package ru.maxsmr.commonutils.data.model;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;

import ru.maxsmr.commonutils.data.FileHelper;

public abstract class InstanceManager<T> {

    protected final File file;

    public InstanceManager(String fileName) {
        FileHelper.testFile(file = new File(fileName));
    }

    @Nullable
    protected abstract byte[] serializeAsByteArray(T inst);

    @Nullable
    protected abstract String serializeAsString(T inst);

    @Nullable
    protected abstract T deserializeFromByteArray(@NonNull byte[] data);

    @Nullable
    protected abstract T deserializeFromString(String data);

    @Nullable
    public static <T extends Serializable> byte[] asByteArray(@Nullable T object) {
        if (object != null) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutput out = null;
            try {
                out = new ObjectOutputStream(bos);
                out.writeObject(object);
                return bos.toByteArray();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (out != null) {
                        out.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    bos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    @Nullable
    public static <T extends Serializable> T fromByteArray(@NonNull Class<T> clazz, @Nullable byte[] data) {
        if (data != null && data.length > 0) {
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInput in = null;
            try {
                in = new ObjectInputStream(bis);
                Object o = in.readObject();
                if (clazz.isAssignableFrom(o.getClass())) {
                    return (T) o;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    bis.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public void saveAsByteArray(@NonNull T inst) {
        FileHelper.writeBytesToFile(serializeAsByteArray(inst), file.getName(), file.getParent(), false);
    }

    public void saveAsString(@NonNull T inst) {
        FileHelper.writeStringToFile(serializeAsString(inst), file.getName(), file.getParent(), false);
    }

    public T loadFromByteArray() {
        byte[] data = FileHelper.readBytesFromFile(file);
        if (data != null && data.length > 0) {
            return deserializeFromByteArray(data);
        }
        return null;
    }

    public T loadFromString() {
        List<String> data = FileHelper.readLinesFromFile(file);
        if (data != null && data.size() > 0) {
            return deserializeFromString(data.get(0));
        }
        return null;
    }

}
