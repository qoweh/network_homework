package com.demo;

import java.util.ArrayList;

public interface BaseLayer {
	public final String layerName = null;
	public final BaseLayer underLayer = null;
	public final ArrayList<BaseLayer> upperLayers = new ArrayList<BaseLayer>();
	public final int upperLayerCount = 0;

	public String GetLayerName();

	public BaseLayer GetUnderLayer();

	public BaseLayer GetUpperLayer(int index);

	public void SetUnderLayer(BaseLayer pUnderLayer);

	public void SetUpperLayer(BaseLayer pUpperLayer);

	public default boolean Send(byte[] input, int length) {
		return false;
	}
	public default boolean Send(String filename) {
		return false;
	}

	public default boolean Receive(byte[] input) {
		return false;
	}

	public default boolean Receive() {
		return false;
	}
}
