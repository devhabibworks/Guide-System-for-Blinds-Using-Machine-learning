package com.habib.finalproject.model;

public class FaceObject {
  private   String name ;
  private   int confident;
    public FaceObject(String name , int confident){
        this.confident = confident;
        this.name  = name ;
    }

    public void setConfident(int confident) {
        this.confident = confident;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getConfident() {
        return confident;
    }

    public String getName() {
        return name;
    }
}
