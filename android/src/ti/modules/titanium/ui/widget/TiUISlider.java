package ti.modules.titanium.ui.widget;

import org.appcelerator.titanium.TiDict;
import org.appcelerator.titanium.TiProxy;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.util.TiConvert;
import org.appcelerator.titanium.view.TiUIView;
import org.appcelerator.titanium.view.TiViewProxy;

import android.widget.SeekBar;

public class TiUISlider extends TiUIView
	implements SeekBar.OnSeekBarChangeListener
{
	private static final String LCAT = "TiUISlider";
	private static final boolean DBG = TiConfig.LOGD;

	private int min;
	private int max;
	private int pos;
	private int offset;

	public TiUISlider(TiViewProxy proxy) {
		super(proxy);
		if (DBG) {
			Log.d(LCAT, "Creating a seekBar");
		}
		this.min = 0;
		this.max = 0;
		this.pos = 0;

		SeekBar seekBar = new SeekBar(proxy.getContext());
		seekBar.setOnSeekBarChangeListener(this);
		setNativeView(seekBar);
	}


	@Override
	public void processProperties(TiDict d)
	{
		super.processProperties(d);

		if (d.containsKey("value")) {
			pos = TiConvert.toInt(d, "value");
		}
		if (d.containsKey("min")) {
			min = TiConvert.toInt(d, "min");
		}
		if (d.containsKey("max")) {
			max = TiConvert.toInt(d, "max");;
		}
		updateControl();
	}

	private void updateControl() {
		offset = -min;
		int length = (int) Math.floor(Math.sqrt(Math.pow(max - min, 2)));
		SeekBar seekBar = (SeekBar) getNativeView();
		seekBar.setMax(length);
		seekBar.setProgress(pos + offset);
	}

	@Override
	public void propertyChanged(String key, Object oldValue, Object newValue, TiProxy proxy)
	{
		if (DBG) {
			Log.d(LCAT, "Property: " + key + " old: " + oldValue + " new: " + newValue);
		}
		SeekBar seekBar = (SeekBar) getNativeView();
		if (key.equals("value")) {
			pos = TiConvert.toInt(newValue);
			seekBar.setProgress(pos + offset);
			onProgressChanged(seekBar, pos, true);
		} else {
			super.propertyChanged(key, oldValue, newValue, proxy);
		}
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		pos = seekBar.getProgress();
		TiDict data = new TiDict();
		data.put("value", scaledValue());
		proxy.internalSetDynamicValue("value", scaledValue(), false);
		proxy.fireEvent("change", data);
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	private int scaledValue() {
		return pos + min;
	}

}
