package org.appcelerator.titanium.view;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.appcelerator.titanium.TiContext;
import org.appcelerator.titanium.TiDict;
import org.appcelerator.titanium.TiProxy;
import org.appcelerator.titanium.util.AsyncResult;
import org.appcelerator.titanium.util.Log;
import org.appcelerator.titanium.util.TiConfig;
import org.appcelerator.titanium.view.TitaniumCompositeLayout.TitaniumCompositeLayoutParams;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.ViewGroup;

public abstract class TiViewProxy extends TiProxy implements Handler.Callback
{
	private static final String LCAT = "TiViewProxy";
	private static final boolean DBG = TiConfig.LOGD;

	private static final int MSG_FIRST_ID = TiProxy.MSG_LAST_ID + 1;

	private static final int MSG_GETVIEW = MSG_FIRST_ID + 100;
	private static final int MSG_FIRE_PROPERTY_CHANGES = MSG_FIRST_ID + 101;
	private static final int MSG_ADD_CHILD = MSG_FIRST_ID + 102;
	private static final int MSG_REMOVE_CHILD = MSG_FIRST_ID + 103;
	private static final int MSG_INVOKE_METHOD = MSG_FIRST_ID + 104;
	private static final int MSG_BLUR = MSG_FIRST_ID + 105;
	private static final int MSG_FOCUS = MSG_FIRST_ID + 106;
	private static final int MSG_SHOW = MSG_FIRST_ID + 107;
	private static final int MSG_HIDE = MSG_FIRST_ID + 108;

	protected static final int MSG_LAST_ID = MSG_FIRST_ID + 999;

	protected ArrayList<TiViewProxy> children;

	private static class InvocationWrapper {
		public String name;
		public Method m;
		public Object target;
		public Object[] args;
	}

	// Ti Properties force using accessors.
	private Double zIndex;
	private int opaque;
	private int opacity;
	private String bgColor; // We've spelled out background in other places.

	private TiUIView view;
	private TiViewProxy window; // TODO, weakReference?

	public TiViewProxy(TiContext tiContext, Object[] args)
	{
		super(tiContext);
		if (args.length > 0) {
			setProperties((TiDict) args[0]);
		}
	}

	//This handler callback is tied to the UI thread.
	public boolean handleMessage(Message msg)
	{
		switch(msg.what) {
			case MSG_GETVIEW : {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleGetView());
				return true;
			}
			case MSG_FIRE_PROPERTY_CHANGES : {
				handleFirePropertyChanges();
				return true;
			}
			case MSG_ADD_CHILD : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleAdd((TiViewProxy) result.getArg());
				result.setResult(null); //Signal added.
				return true;
			}
			case MSG_REMOVE_CHILD : {
				AsyncResult result = (AsyncResult) msg.obj;
				handleRemove((TiViewProxy) result.getArg());
				result.setResult(null); //Signal removed.
				return true;
			}
			case MSG_INVOKE_METHOD : {
				AsyncResult result = (AsyncResult) msg.obj;
				result.setResult(handleInvokeMethod((InvocationWrapper) result.getArg()));
				return true;
			}
			case MSG_BLUR : {
				handleBlur();
				return true;
			}
			case MSG_FOCUS : {
				handleFocus();
				return true;
			}
			case MSG_SHOW : {
				handleShow((TiDict) msg.obj);
				return true;
			}
			case MSG_HIDE : {
				handleHide((TiDict) msg.obj);
				return true;
			}
		}
		return super.handleMessage(msg);
	}

	public Context getContext()
	{
		return getTiContext().getActivity();
	}

	public String getZIndex() {
		return zIndex == null ? (String) null : String.valueOf(zIndex);
	}

	public void setZIndex(String value) {
		if (value != null && value.trim().length() > 0) {
			zIndex = new Double(value);
		}
	}

	public TiUIView peekView()
	{
		return view;
	}

	public TiUIView getView()
	{
		if(getTiContext().isUIThread()) {
			return handleGetView();
		}

		AsyncResult result = new AsyncResult();
		Message msg = getUIHandler().obtainMessage(MSG_GETVIEW, result);
		msg.sendToTarget();
		return (TiUIView) result.getResult();
	}

	protected TiUIView handleGetView()
	{
		if (view == null) {
			if (DBG) {
				Log.i(LCAT, "getView: " + getClass().getSimpleName());
			}

			view = createView();
			modelListener = view;

			// Use a copy so bundle can be modified as it passes up the inheritance
			// tree. Allows defaults to be added and keys removed.

			modelListener.processProperties(dynprops != null ? new TiDict(dynprops) : new TiDict());

			View nativeView = view.getNativeView();
			if (nativeView instanceof ViewGroup) {
				ViewGroup vg = (ViewGroup) nativeView;
				if (children != null) {
					int i = 0;
					for(TiViewProxy p : children) {
						TiUIView v = p.getView();
						v.setParent(this);
						TitaniumCompositeLayout.TitaniumCompositeLayoutParams params = v.getLayoutParams();
						// the index needs to be set. It's consulted as a last resort when considering
						// zIndex
						params.index = i++;
						vg.addView(v.getNativeView(), params);
					}
				}
			} else {
				if (children != null && children.size() > 0) {
					Log.w(LCAT, "Children added to non ViewGroup parent ignored.");
				}
			}
		}
		return view;
	}

	public abstract TiUIView createView();

	public void add(TiViewProxy child) {
		if (children == null) {
			children = new ArrayList<TiViewProxy>();
		}
		if (peekView() != null) {
			if(getTiContext().isUIThread()) {
				handleAdd(child);
				return;
			}

			AsyncResult result = new AsyncResult(child);
			Message msg = getUIHandler().obtainMessage(MSG_ADD_CHILD, result);
			msg.sendToTarget();
			result.getResult(); // We don't care about the result, just synchronizing.
		} else {
			children.add(child);
		}
		//TODO zOrder
	}

	public void handleAdd(TiViewProxy child) {
		View nativeView = view.getNativeView();
		if (nativeView instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) nativeView;
			TiUIView v = child.getView();
			v.setParent(this);
			TitaniumCompositeLayoutParams params = v.getLayoutParams();
			int pos = children.size();
			params.index = pos;
			vg.addView(v.nativeView, params);
			children.add(child);
		} else {
			Log.w(LCAT, "This view is not a ViewGroup, ignoring request to add");
		}
	}

	public void remove(TiViewProxy child)
	{
		if (peekView() != null) {
			if (getTiContext().isUIThread()) {
				handleRemove(child);
				return;
			}

			AsyncResult result = new AsyncResult(child);
			Message msg = getUIHandler().obtainMessage(MSG_REMOVE_CHILD, result);
			msg.sendToTarget();
			result.getResult(); // We don't care about the result, just synchronizing.
		} else {
			if (children != null) {
				children.remove(child);
			}
		}
	}

	public void handleRemove(TiViewProxy child)
	{
		View nativeView = view.getNativeView();
		if (nativeView instanceof ViewGroup) {
			ViewGroup vg = (ViewGroup) nativeView;
			TiUIView v = child.getView();
			v.setParent(null);
			vg.removeView(v.nativeView);
			if (children != null) {
				children.remove(child);
			}
		} else {
			Log.w(LCAT, "This view is not a ViewGroup, ignoring request to add");
		}

	}

	public void show(TiDict options)
	{
		if (getTiContext().isUIThread()) {
			handleShow(options);
		} else {
			getUIHandler().obtainMessage(MSG_SHOW, options).sendToTarget();
		}
	}
	protected void handleShow(TiDict options) {

	}

	public void hide(TiDict options) {
		if (getTiContext().isUIThread()) {
			handleHide(options);
		} else {
			getUIHandler().obtainMessage(MSG_HIDE, options).sendToTarget();
		}

	}
	protected void handleHide(TiDict options) {

	}

	public void animate(TiViewProxy view) {

	}
	public void blur()
	{
		if (getTiContext().isUIThread()) {
			handleBlur();
		} else {
			getUIHandler().sendEmptyMessage(MSG_BLUR);
		}
	}
	protected void handleBlur() {
		if (view != null) {
			view.blur();
		}
	}
	public void focus()
	{
		if (getTiContext().isUIThread()) {
			handleFocus();
		} else {
			getUIHandler().sendEmptyMessage(MSG_FOCUS);
		}
	}
	protected void handleFocus() {
		if (view != null) {
			view.focus();
		}
	}


	// Helper methods

	private void firePropertyChanges() {
		if (getTiContext().isUIThread()) {
			handleFirePropertyChanges();
		} else {
			getUIHandler().sendEmptyMessage(MSG_FIRE_PROPERTY_CHANGES);
		}
	}

	private void handleFirePropertyChanges() {
		if (modelListener != null && dynprops != null) {
			for (String key : dynprops.keySet()) {
				modelListener.propertyChanged(key, null, dynprops.get(key), this);
			}
		}
	}

	@Override
	public Object resultForUndefinedMethod(String name, Object[] args)
	{
		if (view != null) {
			Method m = getTiContext().getTiApp().methodFor(view.getClass(), name);
			if (m != null) {
				InvocationWrapper w = new InvocationWrapper();
				w.name = name;
				w.m = m;
				w.target = view;
				w.args = args;

				if (getTiContext().isUIThread()) {
					handleInvokeMethod(w);
				} else {
					AsyncResult result = new AsyncResult(w);
					Message msg = getUIHandler().obtainMessage(MSG_INVOKE_METHOD, result);
					msg.sendToTarget();
					return result.getResult();
				}
			}
		}

		return super.resultForUndefinedMethod(name, args);
	}

	private Object handleInvokeMethod(InvocationWrapper w)
	{
		try {
			return w.m.invoke(w.target, w.args);
		} catch (InvocationTargetException e) {
			Log.e(LCAT, "Error while invoking " + w.name + " on " + view.getClass().getSimpleName(), e);
			// TODO - wrap in a better exception.
			return e;
		} catch (IllegalAccessException e) {
			Log.e(LCAT, "Error while invoking " + w.name + " on " + view.getClass().getSimpleName(), e);
			return e;
		}
	}
}
