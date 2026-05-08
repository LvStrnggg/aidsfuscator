package dev.lvstrng.aidsfuscator.tree;

import dev.lvstrng.aidsfuscator.context.Context;

public class JResource {
    private String name;
    private byte[] data;
    private final Context context;

    public JResource(String name, byte[] data, Context context) {
        this.data = data;
        this.name = name;
        this.context = context;
    }

    public JResource(String name, Context context) {
        this.name = name;
        this.context = context;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Context getContext() {
        return context;
    }
}
