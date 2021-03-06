package com.softmed.htmr_chw.Domain;

import android.app.Activity;
import android.app.DialogFragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import com.google.common.base.Strings;

import org.ei.opensrp.R;

import org.ei.opensrp.view.activity.SecuredNativeSmartRegisterActivity;
import org.ei.opensrp.view.dialog.DialogOptionModel;
import org.opensrp.api.domain.Location;
import org.opensrp.api.util.EntityUtils;
import org.opensrp.api.util.LocationTree;

import java.util.Map;

import atv.holder.SelectableItemHolder;
import atv.model.TreeNode;
import atv.view.AndroidTreeView;

import static org.ei.opensrp.util.StringUtil.*;

public class LocationSelectorDialogFragment extends DialogFragment {
    private static final String TAG = LocationSelectorDialogFragment.class.getSimpleName();
    private static final String LocationJSONString = "locationJSONString";
    private static final String FormName = "formName";

    public static String savestate;
    AndroidTreeView tView;

    OnLocationSelectedListener mCallback;

    public static LocationSelectorDialogFragment newInstance(
            SecuredNativeSmartRegisterActivity activity,
            DialogOptionModel dialogOptionModel,
            String locationJSONString, String formname) {

        LocationSelectorDialogFragment lsd = new LocationSelectorDialogFragment();
        Bundle bundle = new Bundle(2);
        bundle.putString(LocationJSONString, locationJSONString);
        bundle.putString(FormName, formname);
        lsd.setArguments(bundle);
        return lsd;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mCallback = (OnLocationSelectedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement OnLocationSelectedListener.");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Holo_Light_Dialog);
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container,
                             Bundle savedInstanceState) {
        ViewGroup dialogView = new LinearLayout(getActivity());
        TreeNode root = TreeNode.root();

        LocationTree locationTree = EntityUtils.fromJson(getArguments().getString(LocationJSONString), LocationTree.class);

        Map<String, org.opensrp.api.util.TreeNode<String, Location>> locationMap =
                locationTree.getLocationsHierarchy();

        // creating the tree
        locationTreeToTreNode(root, locationMap, getArguments().getString(FormName));

        tView = new AndroidTreeView(getActivity(), root);
        tView.setDefaultContainerStyle(R.style.TreeNodeStyle);
        tView.setSelectionModeEnabled(false);

        if (savestate != null) {
            tView.restoreState(savestate);
        }

        // tView.getSelected().get(1).
        dialogView.addView(tView.getView());
        return dialogView;
    }

    public TreeNode createNode(String locationId, String locationlevel, String locationname, String formName) {
        TreeNode node = new TreeNode(locationId, locationname, locationlevel).setViewHolder(new SelectableItemHolder(getActivity(), locationlevel + ": "));
        node.setSelectable(false);
        addselectlistener(node, formName);
        return node;
    }

    public void addChildToParentNode(TreeNode parent, TreeNode[] nodes) {
        for (int i = 0; i < nodes.length; i++) {
            parent.addChild(nodes[i]);
        }
    }

    public void addselectlistener(TreeNode node, final String formName) {
        node.setClickListener(new TreeNode.TreeNodeClickListener() {
            @Override
            public void onClick(TreeNode node, Object value) {
                if (node.isLeaf()) {
                    String locationjson = "";
                    TreeNode traversingnode = node;
//                    locationjson = traversingnode.getLocationId();
                    locationjson = traversingnode.getName();
//
                    if (mCallback != null) {
                        mCallback.OnLocationSelected(locationjson);
                    }
                    savestate = tView.getSaveState();
                    dismiss();
                }
            }
        });
    }

    public void locationTreeToTreNode(TreeNode node, Map<String, org.opensrp.api.util.TreeNode<String, Location>> location, String formName) {

        for (Map.Entry<String, org.opensrp.api.util.TreeNode<String, Location>> entry : location.entrySet()) {

            String locationId = entry.getValue().getId();
            String locationTag = "";
            try {
                locationTag = entry.getValue().getNode().getTags().iterator().next();
            } catch (Exception e) {
                e.printStackTrace();
            }
            TreeNode tree = createNode(locationId,
                    Strings.isNullOrEmpty(locationTag) ? "-" : humanize(locationTag),
                    humanize(entry.getValue().getLabel()),
                    formName);
            node.addChild(tree);
            if (entry.getValue().getChildren() != null) {
                locationTreeToTreNode(tree, entry.getValue().getChildren(), formName);
            }

        }
    }

    public interface OnLocationSelectedListener {
        public void OnLocationSelected(String locationSelected);
    }

}