package edu.usc.splitsmart.presentation.groups;

import android.content.Intent;
import android.os.Bundle;
import android.widget.*;
import androidx.lifecycle.ViewModelProvider;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.*;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class GroupDetailScreen extends ProtectedScreen {
    private GroupDetailViewModel model;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_group_detail);
        String id = getIntent().getStringExtra(NavigationContract.GROUP_ID);
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) {
            text(R.id.access_status, getString(R.string.groups_missing_scope)); show(R.id.access_retry, false); show(R.id.access_logout, false); return;
        }
        model = new ViewModelProvider(this, new FeatureFactory(this, getIntent().getExtras(),
            saved -> new GroupDetailViewModel(saved, services().groups(), id))).get(GroupDetailViewModel.class);
        bind(model, R.id.groups_refresh, R.id.groups_save, R.id.groups_add, R.id.groups_delete);
        model.group.observe(this, group -> {
            if (group == null) {
                show(R.id.groups_owner_controls, false);
                text(R.id.feature_title, getString(R.string.groups_details_title));
                text(R.id.feature_intro, ""); text(R.id.groups_description_view, ""); text(R.id.groups_role, "");
                return;
            }
            text(R.id.feature_title, group.name()); text(R.id.feature_intro, getString(R.string.groups_details_intro));
            text(R.id.groups_description_view, group.description() == null ? "" : group.description());
            boolean owner = group.ownerId().equals(currentUid); show(R.id.groups_owner_controls, owner);
            text(R.id.groups_role, getString(owner ? R.string.groups_owner_label : R.string.groups_member_label));
            if (!model.pending() && !findViewById(R.id.groups_name).hasFocus()) text(R.id.groups_name, group.name());
            if (!model.pending() && !findViewById(R.id.groups_description).hasFocus()) text(R.id.groups_description, group.description() == null ? "" : group.description());
        });
        model.members.observe(this, members -> {
            LinearLayout rows = findViewById(R.id.groups_members_rows); rows.removeAllViews();
            for (MemberProfile member : members) {
                android.view.View row = getLayoutInflater().inflate(R.layout.row_group, rows, false);
                row.setClickable(false); row.setFocusable(false);
                ((TextView) row.findViewById(R.id.row_name)).setText(member.name());
                ((TextView) row.findViewById(R.id.row_detail)).setText(member.email()); rows.addView(row);
            }
        });
        model.deleted.observe(this, result -> {
            if (result == null) return;
            setResult(RESULT_OK, new Intent().putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, result.value().groupId())
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue())); finish();
        });
        model.changed.observe(this, result -> {
            if (result != null) setResult(RESULT_OK, new Intent()
                .putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, result.value().groupId())
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue()));
        });
        findViewById(R.id.groups_refresh).setOnClickListener(v -> model.refresh());
        findViewById(R.id.groups_save).setOnClickListener(v -> model.update(input(R.id.groups_name), input(R.id.groups_description)));
        findViewById(R.id.groups_add).setOnClickListener(v -> model.add(input(R.id.groups_email)));
        findViewById(R.id.groups_delete).setOnClickListener(v -> new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.groups_delete_title).setMessage(R.string.groups_delete_message)
            .setNegativeButton(R.string.groups_cancel, null).setPositiveButton(R.string.groups_delete, (dialog, which) -> model.delete()).show());
        findViewById(R.id.identity_retry).setOnClickListener(v -> model.retry()); protect();
    }
    @Override protected void onAuthorized(User user) { if (model != null) { model.bindUser(user.uid()); model.refresh(); } }
    @Override protected void onAccessLost() { if (model != null) { model.cancelOperations(); model.group.setValue(null); model.members.setValue(java.util.List.of()); } }
}
