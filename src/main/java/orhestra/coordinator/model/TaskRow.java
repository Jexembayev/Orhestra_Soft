// src/main/java/orhestra/ui/model/TaskRow.java
package orhestra.coordinator.model;

import javafx.beans.property.*;

public class TaskRow {
    private final StringProperty id = new SimpleStringProperty();
    private final StringProperty alg = new SimpleStringProperty();
    private final StringProperty func = new SimpleStringProperty();
    private final IntegerProperty iter = new SimpleIntegerProperty();
    private final StringProperty runtime = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();
    private final StringProperty progress = new SimpleStringProperty();

    public static TaskRow of(String id, String alg, String func, int iter, String runtime, String status, String progress){
        var r = new TaskRow();
        r.setId(id); r.setAlg(alg); r.setFunc(func); r.setIter(iter);
        r.setRuntime(runtime); r.setStatus(status); r.setProgress(progress);
        return r;
    }

    public StringProperty idProperty(){return id;}       public void setId(String v){id.set(v);}
    public StringProperty algProperty(){return alg;}     public void setAlg(String v){alg.set(v);}
    public StringProperty funcProperty(){return func;}   public void setFunc(String v){func.set(v);}
    public IntegerProperty iterProperty(){return iter;}  public void setIter(int v){iter.set(v);}
    public StringProperty runtimeProperty(){return runtime;} public void setRuntime(String v){runtime.set(v);}
    public StringProperty statusProperty(){return status;}   public void setStatus(String v){status.set(v);}
    public StringProperty progressProperty(){return progress;} public void setProgress(String v){progress.set(v);}
}

