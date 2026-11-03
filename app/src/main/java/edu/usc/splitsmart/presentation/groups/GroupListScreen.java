package edu.usc.splitsmart.presentation.groups;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.User;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.identity.ProfileScreen;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class GroupListScreen extends ProtectedScreen {
    private GroupListViewModel model;
    private final ActivityResultLauncher<Intent> child = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
        if (result.getResultCode() == RESULT_OK && session != null) session.refresh();
    });
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_groups_list); show(R.id.feature_back, false);
        model = new ViewModelProvider(this, new FeatureFactory(this, null,
            saved -> new GroupListViewModel(saved, services().groups()))).get(GroupListViewModel.class);
        bind(model, R.id.groups_refresh, R.id.groups_create, R.id.groups_profile);
        model.list.observe(this, groups -> {
            LinearLayout rows = findViewById(R.id.groups_rows); rows.removeAllViews(); show(R.id.groups_empty, groups.isEmpty());
            for (var group : groups) {
                View row = getLayoutInflater().inflate(R.layout.row_group, rows, false);
                ((TextView) row.findViewById(R.id.row_name)).setText(group.name());
                ((TextView) row.findViewById(R.id.row_detail)).setText(getResources().getQuantityString(R.plurals.groups_count, group.memberIds().size(), group.memberIds().size()));
                row.setOnClickListener(v -> child.launch(new Intent(this, GroupDetailScreen.class).putExtra(NavigationContract.GROUP_ID, group.groupId())));
                rows.addView(row);
            }
        });
        findViewById(R.id.groups_create).setOnClickListener(v -> child.launch(new Intent(this, CreateGroupScreen.class)));
        findViewById(R.id.groups_profile).setOnClickListener(v -> startActivity(new Intent(this, ProfileScreen.class)));
        findViewById(R.id.groups_refresh).setOnClickListener(v -> model.refresh());
        findViewById(R.id.identity_logout).setOnClickListener(v -> session.signOut()); protect();
    }
    @Override protected void onAuthorized(User user) { model.bindUser(user.uid()); model.refresh(); }
    @Override protected void onAccessLost() { if (model != null) { model.cancelOperations(); model.list.setValue(java.util.List.of()); } }
}
