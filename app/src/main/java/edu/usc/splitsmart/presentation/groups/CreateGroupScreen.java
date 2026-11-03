package edu.usc.splitsmart.presentation.groups;

import android.content.Intent;
import android.os.Bundle;
import androidx.lifecycle.ViewModelProvider;
import edu.usc.splitsmart.R;
import edu.usc.splitsmart.domain.model.User;
import edu.usc.splitsmart.presentation.common.*;
import edu.usc.splitsmart.presentation.navigation.NavigationContract;

public final class CreateGroupScreen extends ProtectedScreen {
    private GroupListViewModel model;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); screen(R.layout.screen_group_create);
        model = new ViewModelProvider(this, new FeatureFactory(this, null,
            saved -> new GroupListViewModel(saved, services().groups()))).get(GroupListViewModel.class);
        bind(model, R.id.groups_create);
        findViewById(R.id.groups_create).setOnClickListener(v -> model.create(input(R.id.groups_name), input(R.id.groups_description)));
        findViewById(R.id.identity_retry).setOnClickListener(v -> model.create(input(R.id.groups_name), input(R.id.groups_description)));
        model.created.observe(this, result -> {
            if (result == null) return;
            setResult(RESULT_OK, new Intent().putExtra(NavigationContract.RESULT_REQUEST_ID, result.requestId())
                .putExtra(NavigationContract.RESULT_GROUP_ID, result.value().groupId())
                .putExtra(NavigationContract.RESULT_GROUP_REVISION, result.groupRevision().longValue())); finish();
        }); protect();
    }
    @Override protected void onAuthorized(User user) { model.bindUser(user.uid()); }
    @Override protected void onAccessLost() { if (model != null) model.cancelOperations(); }
}
