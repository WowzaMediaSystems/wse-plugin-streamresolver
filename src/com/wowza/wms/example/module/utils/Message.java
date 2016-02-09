package com.wowza.wms.example.module.utils;

public class Message {
	public String streamName="";
	public String appName="";
	public String appInstance="";
	
	public String toString(){
		return this.appName+"|"+this.appInstance+"|"+this.streamName;
	}
}