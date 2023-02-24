package org.tensorflow.lite.examples.detection;

public class DetectedObject {
  private   String name;
  private ObjectLocation location;
  private int counter;
  String personName;

  public DetectedObject(String name, ObjectLocation location){
      this.name = name;
      this.location = location;
      counter = 1;
  }
    public DetectedObject(String name, ObjectLocation location , String personName){
        this.name = name;
        this.location = location;
        this.personName = personName;
        counter = 1;
    }
    public void setPersonKnown(String personName) {
        this.personName = personName;
    }

    public String getPersonName() {
        return personName;
    }

    public void  setName(String name){
        this.name = name;
    }
  public   String getName(){return name;}

   public void setLocation(ObjectLocation location){this.location  = location;}
   public ObjectLocation getLocation(){return  location;}

    public void setCounter(int counter) {
        this.counter = counter;
    }

    public int getCounter() {
        return counter;
    }
    public void  incCounter(){
         counter ++;
    }

   public boolean isEqual( DetectedObject object){
       return  (object.getName().equals(name) && object.getLocation() == location);
    }
}
