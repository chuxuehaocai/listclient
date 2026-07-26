package dev.naominet.listclient.comamnd;

public abstract class Command {
    private final String name;
    private String[] otherNames;
    private final String desc;

    public Command(String name, String desc) {
        this.name = name;
        this.otherNames = new String[]{};
        this.desc = desc;
    }

    public String[] getOtherNames() {
        return otherNames;
    }

    public Command(String name, String[] otherNames, String desc) {
        this.name = name;
        this.otherNames = otherNames;
        this.desc = desc;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public abstract void run(String[] args);
}
