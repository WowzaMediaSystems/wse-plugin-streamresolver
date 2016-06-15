/*
 * This code and all components (c) Copyright 2006 - 2016, Wowza Media Systems, LLC. All rights reserved.
 * This code is licensed pursuant to the Wowza Public License version 1.0, available at www.wowza.com/legal.
 */
package com.wowza.wms.plugin.streamresolver;

public class Message
{
	public String streamName = "";
	public String appName = "";
	public String appInstance = "";

	@Override
	public String toString()
	{
		return appName + "|" + appInstance + "|" + streamName;
	}
}
