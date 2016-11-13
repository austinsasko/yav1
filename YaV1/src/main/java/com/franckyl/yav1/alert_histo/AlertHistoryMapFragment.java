package com.franckyl.yav1.alert_histo;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.franckyl.yav1.R;
import com.franckyl.yav1.utils.GMapUtils;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;

import java.util.Arrays;
import java.util.List;

/**
 * Created by franck on 2/3/14.
 */
public class AlertHistoryMapFragment extends MapFragment
{
    private GoogleMap     map = null;
    private CameraUpdate  mZoom       = null;
    private boolean       mMapReady   = false;
    private int           mFailure    = 0;

    // create the fragment activity

    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        mFailure = GMapUtils.getGMapError(getActivity());
    }

    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        // error case (no connection)

        map = getMap();
        // the info adapter
        if(map != null && mFailure == 0)
        {
            map.setMyLocationEnabled(false);
            mMapReady = false;
            // set a listener to zoom when the map is ready

            map.setOnCameraChangeListener(new GoogleMap.OnCameraChangeListener()
            {
                @Override
                public void onCameraChange(CameraPosition arg0)
                {
                    // Move camera.
                    if(mZoom != null)
                    {
                        map.moveCamera(mZoom);
                    }
                    // Remove listener to prevent position reset on camera move.
                    map.setOnCameraChangeListener(null);
                    mMapReady = true;
                    createMarker(true);
                }
            });

            // we would recreate the location

            map.setInfoWindowAdapter(new GoogleMap.InfoWindowAdapter()
            {
                @Override
                public View getInfoWindow(Marker marker)
                {
                    String v = marker.getSnippet();
                    // explode on ,
                    List<String> items;
                    items = Arrays.asList(v.split("\\s*,\\s*"));
                    int imgId = Integer.valueOf(items.get(0));
                    int prop = Integer.valueOf(items.get(1));
                    v = items.get(2);

                    // create the View
                    View view = (getActivity().getLayoutInflater()).inflate(R.layout.alert_histo_custom_marker, null);
                    // search for the image
                    ImageView img = (ImageView) view.findViewById(R.id.img);
                    img.setImageResource(imgId);
                    img = (ImageView) view.findViewById(R.id.mute);
                    img.setImageResource((prop & 32) > 0 ? R.drawable.mute_on_user : R.drawable.mute_off);

                    TextView txt = (TextView) view.findViewById(R.id.snippet);
                    txt.setText(v);
                    txt = (TextView) view.findViewById(R.id.title);
                    txt.setText(marker.getTitle());
                    return view;
                }

                @Override
                public View getInfoContents(Marker marker)
                {
                    return null;
                }
            });
        }

        String t = getTag();
        ( (AlertHistoryActivity) getActivity()).setFragmentTag(2, t);
    }

    public void onStart()
    {
        super.onStart();
    }

    public void onAttach(Activity activity)
    {
        super.onAttach(activity);
    }

    public void onStop()
    {
        super.onStop();
    }

    public void onDestroyView()
    {
        super.onDestroyView();
    }

    public void clearMap()
    {
        if(map != null)
            map.clear();
    }

    public void setUserVisibleHint(final boolean visible)
    {
        super.setUserVisibleHint(visible);
        // Load areas if visible and view was already created

        if(visible)
        {
            if(mFailure == 0)
            {
                if(AlertHistoryActivity.mAlertList.hasChanged() && mMapReady)
                    createMarker(false);
            }
            else
            {
                // show the error
                new AlertDialog.Builder(getActivity())
                        .setMessage(mFailure)
                        .setPositiveButton(R.string.ok, null).show();
            }
        }
    }

    private void createMarker(boolean fromCreate)
    {
        AlertHistoryList l = AlertHistoryActivity.mAlertList;
        // we show the marker
        for(AlertHistory a: l)
            a.updateMarker(map, fromCreate);

        mZoom = l.getZoom();

        if(mZoom != null)
            map.moveCamera(mZoom);

        AlertHistoryActivity.mAlertList.resetChanged(false);
    }
}
