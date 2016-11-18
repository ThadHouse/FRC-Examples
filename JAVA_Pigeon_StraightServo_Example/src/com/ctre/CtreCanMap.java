/**
 * This PigeonImu class is complied with the CTRE FRC Toolchain, distributed by CTRE.
 * However for pre-2017 FRC installations, this class can be directly compiled into the Eclipse project.
 */

/*
 *  Software License Agreement
 *
 * Copyright (C) Cross The Road Electronics.  All rights
 * reserved.
 * 
 * Cross The Road Electronics (CTRE) licenses to you the right to 
 * use, publish, and distribute copies of CRF (Cross The Road) firmware files (*.crf) and Software
 * API Libraries ONLY when in use with Cross The Road Electronics hardware products.
 * 
 * THE SOFTWARE AND DOCUMENTATION ARE PROVIDED "AS IS" WITHOUT
 * WARRANTY OF ANY KIND, EITHER EXPRESS OR IMPLIED, INCLUDING WITHOUT
 * LIMITATION, ANY WARRANTY OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, TITLE AND NON-INFRINGEMENT. IN NO EVENT SHALL
 * CROSS THE ROAD ELECTRONICS BE LIABLE FOR ANY INCIDENTAL, SPECIAL, 
 * INDIRECT OR CONSEQUENTIAL DAMAGES, LOST PROFITS OR LOST DATA, COST OF
 * PROCUREMENT OF SUBSTITUTE GOODS, TECHNOLOGY OR SERVICES, ANY CLAIMS
 * BY THIRD PARTIES (INCLUDING BUT NOT LIMITED TO ANY DEFENSE
 * THEREOF), ANY CLAIMS FOR INDEMNITY OR CONTRIBUTION, OR OTHER
 * SIMILAR COSTS, WHETHER ASSERTED ON THE BASIS OF CONTRACT, TORT
 * (INCLUDING NEGLIGENCE), BREACH OF WARRANTY, OR OTHERWISE
 */
package com.ctre;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import edu.wpi.first.wpilibj.can.CANJNI;

public class CtreCanMap {

	public class RxEvent {
		public long _data = 0;
		public long _time = 0;
		public int _len = 0;

		public RxEvent() {
		}

		public RxEvent(long data, long time, int len) {
			_data = data;
			_time = time;
			_len = len;
		}

		public RxEvent clone() {
			return new RxEvent(_data, _time, _len);
		}
		
		public void Copy(RxEvent src)
		{
			_data = src._data;
			_time = src._time;
			_len = src._len;
		}
	};

	Map<Integer, RxEvent> _map = new HashMap<Integer, RxEvent>();

	protected int GetRx(int arbId, int timeoutMs, RxEvent toFill, boolean allowStale) {
		CTR_Code retval = CTR_Code.CTR_RxTimeout;
		/* cap timeout at 999ms */
		if(timeoutMs > 999)
			timeoutMs = 999;
		if(timeoutMs < 100)
			timeoutMs = 100;

		/* call into JNI to get message */
		try {
	
		    ByteBuffer targetedMessageID = ByteBuffer.allocateDirect(4);
		    targetedMessageID.order(ByteOrder.LITTLE_ENDIAN);
		    
		    targetedMessageID.asIntBuffer().put(0, arbId);
		
		    ByteBuffer timeStamp = ByteBuffer.allocateDirect(4);
		 
		    // Get the data.
		    ByteBuffer dataBuffer =
		        CANJNI.FRCNetCommCANSessionMuxReceiveMessage(targetedMessageID.asIntBuffer(),
		        		0xFFFFFFFF, timeStamp);

		    if(( dataBuffer != null) && (timeStamp != null)) {
		    	/* fresh message */
		    	toFill._len = dataBuffer.capacity();
		    	toFill._data = 0;
		    	if(toFill._len > 0){
		    		int lenMinusOne = toFill._len - 1; 
		    		for (int i = 0; i < toFill._len; i++) {
		    			/* grab byte without sign extensions */
		    			long aByte = dataBuffer.get(lenMinusOne-i);
		    			aByte &= 0xFF;
		    			/* stuff little endian */
		    			toFill._data <<= 8;
		    			toFill._data |= aByte;
		    		}
		    	}
				toFill._time = System.currentTimeMillis();

				/* store it */
				_map.put(arbId, toFill.clone());
				retval = CTR_Code.CTR_OKAY;
		    }
		    else 
		    {
		    	/* no message */
		    	retval = CTR_Code.CTR_RxTimeout;
		    }

		} catch (Exception e) {
			/* no message, check the cache*/
			retval = CTR_Code.CTR_RxTimeout;
		}

		if (retval != CTR_Code.CTR_OKAY) {
			if(allowStale == false) {
				/* caller does not want old data */
			} else {
				/* lookup object first */
				RxEvent lookup = (RxEvent)_map.get(arbId);
				/* was a message received before */
				if (lookup == null)
				{
					/* leave retval nonzero */
				}
				else 
				{
					/* check how old the object is */
					long now  = System.currentTimeMillis();
					long timeSince = now - lookup._time;
					
					if(timeSince > timeoutMs)
					{
						/* at least copy the last received despite being old */
						toFill.Copy(lookup);
	
						/* too old, leave retval nonzero */
					}
					else
					{
						/* copy to caller's object */
						toFill.Copy(lookup);
						retval = CTR_Code.CTR_OKAY;
					}
				}	
			}	
		}
		return retval.IntValue();
	}
}
