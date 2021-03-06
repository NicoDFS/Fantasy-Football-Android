package com.daniels.harry.assignment.activity;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SearchView;
import android.view.Menu;
import android.view.MenuItem;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.daniels.harry.assignment.R;
import com.daniels.harry.assignment.adapter.FavouriteTeamListViewAdapter;
import com.daniels.harry.assignment.constant.Constants;
import com.daniels.harry.assignment.databinding.ActivityFavouritePickerBinding;
import com.daniels.harry.assignment.dialog.ConfirmDialogs;
import com.daniels.harry.assignment.dialog.ErrorDialogs;
import com.daniels.harry.assignment.handler.HttpRequestHandler;
import com.daniels.harry.assignment.jsonobject.AllTeamsJson;
import com.daniels.harry.assignment.listener.OnDbGetAsyncListener;
import com.daniels.harry.assignment.listener.OnDbSaveAsyncListener;
import com.daniels.harry.assignment.mapper.FavouriteTeamMapper;
import com.daniels.harry.assignment.model.FavouriteTeam;
import com.daniels.harry.assignment.repository.DbGetAllAsync;
import com.daniels.harry.assignment.repository.DbSaveAllAsync;
import com.daniels.harry.assignment.singleton.CurrentUser;
import com.daniels.harry.assignment.util.Calculators;
import com.daniels.harry.assignment.viewmodel.FavouriteTeamPickerViewModel;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class FavouritePickerActivity
        extends AppCompatActivity
        implements LocationListener, GoogleApiClient.ConnectionCallbacks,
                   GoogleApiClient.OnConnectionFailedListener, SearchView.OnQueryTextListener,
                   RequestQueue.RequestFinishedListener, FavouriteTeamListViewAdapter.Listener,
                   DialogInterface.OnClickListener, OnDbSaveAsyncListener, OnDbGetAsyncListener {

    private static final Comparator<FavouriteTeamPickerViewModel> DISTANCE_COMPARATOR = new Comparator<FavouriteTeamPickerViewModel>() {
        @Override
        public int compare(FavouriteTeamPickerViewModel a, FavouriteTeamPickerViewModel b) {
            return Float.compare(a.getDistance(), b.getDistance());
        }
    };

    private List<FavouriteTeamPickerViewModel> mViewModels = new ArrayList<>();
    private List<FavouriteTeam> mTeams = new ArrayList<>();

    private FavouriteTeamPickerViewModel mSelectedViewModel;

    private AllTeamsJson mTeamsJson;

    private FavouriteTeamListViewAdapter mListViewAdapter;
    private ActivityFavouritePickerBinding mBinding;
    private ProgressDialog mProgressDialog;

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private HttpRequestHandler mRequestHandler;

    private boolean mViewModelsLoaded;

    // instantiate http request handler, google api client and databinding
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = DataBindingUtil.setContentView(this, R.layout.activity_favourite_picker);

        mRequestHandler = new HttpRequestHandler(this, this, this);
        mRequestHandler.addRequestFinishedListener();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        mListViewAdapter = new FavouriteTeamListViewAdapter(this, DISTANCE_COMPARATOR, this);

        mBinding.listFavouritePicker.setLayoutManager(new LinearLayoutManager(this));
        mBinding.listFavouritePicker.setAdapter(mListViewAdapter);

        getData();
    }

    @Override
    protected void onStop()
    {
        if (mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }

        mRequestHandler.removeRequestFinishedListener();

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_search, menu);

        final MenuItem searchItem = menu.findItem(R.id.action_search);
        final SearchView searchView = (SearchView) MenuItemCompat.getActionView(searchItem);

        searchView.setOnQueryTextListener(this);

        return true;
    }

    // handle the change of text inputted into the search bar to filter the list of teams
    @Override
    public boolean onQueryTextChange(String query) {
        final List<FavouriteTeamPickerViewModel> filteredModelList = filter(mViewModels, query);

        resetListAdapter(filteredModelList);

        return true;
    }
    @Override
    public boolean onQueryTextSubmit(String query) {
        return false;
    }

    private static List<FavouriteTeamPickerViewModel> filter (List<FavouriteTeamPickerViewModel> models, String query)
    {
        final String lowerCaseQuery = query.toLowerCase();

        final List<FavouriteTeamPickerViewModel> filteredModelList = new ArrayList<>();
        for (FavouriteTeamPickerViewModel model : models) {
            final String name = model.getTeamName().toLowerCase();
            if (name.contains(lowerCaseQuery)) {
                filteredModelList.add(model);
            }
        }

        return filteredModelList;
    }

    // upon location received, stop requesting location updates and calculate distance values
    @Override
    public void onLocationChanged(Location location) {

        if (mViewModelsLoaded && mProgressDialog != null)
        {
            updateViewModelLocation(location);
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mProgressDialog.dismiss();
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
            requestLocationUpdates();
    }

    @Override
    public void onConnectionSuspended(int i) {
        mProgressDialog.dismiss();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        ErrorDialogs.showErrorDialog(this,
                getString(R.string.dialog_title_generic_error),
                getString(R.string.dialog_message_generic_error)
                + connectionResult.getErrorMessage());
    }

    // upon the permission or denial of location services to be used by the app, either request location updates or show an error dialog
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case  Constants.REQUEST_LOCATION_PERMISSION : {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    requestLocationUpdates();
                } else {
                    ErrorDialogs.showErrorDialog(this,
                            getString(R.string.dialog_title_location_error),
                            getString(R.string.dialog_message_location_error));
                }
            }
        }
    }

    // upon http request response received, handle json object transformation
    @Override
    public void onRequestFinished(Request request) {
        switch (request.getTag().toString())
        {
            case Constants.REQUEST_TEAMS: {
                handleTeamsResponse();
                break;
            }
        }
    }

    // upon the selection of a team listview item, show a confirmation dialog
    @Override
    public void onClick(FavouriteTeamPickerViewModel model) {
        mSelectedViewModel = model;
        ConfirmDialogs.showConfirmFavouriteDialog(this, this, model.getTeamName());
    }

    // upon confirmation of a team selection, save this choice to the database
    @Override
    public void onClick(DialogInterface dialog, int which) {
        CurrentUser user = CurrentUser.getInstance();
        FavouriteTeam team = user.getFavouriteTeam() != null ? user.getFavouriteTeam() : new FavouriteTeam();
        team.name = mSelectedViewModel.getTeamName();
        team.apiId = mSelectedViewModel.getId();
        team.distance = mSelectedViewModel.getDistance();
        team.populated = false;
        team.save();
        user.setFavouriteTeam(team);
        finish();
    }

    // check that the application has location permissions and request the commence of location updates,
    // if the user has not granted permissions, prompt the user to do so
    private void requestLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED) {

            LocationManager locationManager = (LocationManager)getSystemService(Context.LOCATION_SERVICE);

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                    || locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {

                mLocationRequest = LocationRequest.create();
                mLocationRequest.setInterval(100);
                mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

                LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);

                mProgressDialog = ProgressDialog.show(this,
                        getString(R.string.dialog_title_location_progress),
                        getString(R.string.please_wait),
                        true, true);
            } else {
                ErrorDialogs.showErrorDialog(this,
                        getString(R.string.dialog_title_location_error),
                        getString(R.string.dialog_message_location_error));
            }
        }
        else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, Constants.REQUEST_LOCATION_PERMISSION);
        }
    }

    // create a new http request to receive all teams in JSON format
    private void handleHttpRequest(){
        mRequestHandler.sendJsonObjectRequest(getString(R.string.team_api_endpoint),
                Constants.REQUEST_TEAMS,
                AllTeamsJson.class);
    }

    // upon the response of the http request, map the json objects to database objects and save
    private void handleTeamsResponse() {
        mTeamsJson = (AllTeamsJson) mRequestHandler.getResultObject();

        List<FavouriteTeam> teams = FavouriteTeamMapper.jsonToModels(mTeamsJson);
        mTeams = teams;

        DbSaveAllAsync<FavouriteTeam> save = new DbSaveAllAsync<>(teams, this, Constants.DB_TEAMS_TAG);
        save.execute();
    }

    // repopulate the listview with new data
    private void resetListAdapter(List<FavouriteTeamPickerViewModel> items){
        mListViewAdapter.edit().removeAll().commit();
        mListViewAdapter.edit().add(items).commit();
        mBinding.listFavouritePicker.scrollToPosition(0);
    }

    // calculate distance values for viewmodel objects
    private void updateViewModelLocation(Location location)
    {
        for (FavouriteTeamPickerViewModel vm : mViewModels) {
            vm.setDistance(Calculators.calculateDistance(vm.getGroundLat(),
                    vm.getGroundLong(),
                    (float)location.getLatitude(),
                    (float)location.getLongitude()));
        }

        resetListAdapter(mViewModels);
    }

    // map database team objects to a list of viewmodels
    private void setViewModels(List<FavouriteTeam> teams) {
        for (FavouriteTeam team : teams) {
            mViewModels.add(FavouriteTeamMapper.modelToPickerViewModel(team));
        }
        mViewModelsLoaded = true;
    }

    // handle the process of data retreival by checking for a valid network connection or falling back to use the database if any data exists
    // if not, ask the user to try again with a network connection through a dialog box
    private void getData() {
        if (mRequestHandler.isNetworkConnected()) {
            connectToGoogleApiClient();
            handleHttpRequest();
        } else if(FavouriteTeam.count(FavouriteTeam.class) > 0) {
            connectToGoogleApiClient();
            DbGetAllAsync<FavouriteTeam> get = new DbGetAllAsync<>(FavouriteTeam.class, this, Constants.DB_TEAMS_TAG);
            get.execute();
        } else {
            ErrorDialogs.showErrorDialog(this,
                    getString(R.string.dialog_title_noteams_error),
                    getString(R.string.dialog_message_noteams_error));
        }
    }

    private void connectToGoogleApiClient() {
        if (mGoogleApiClient != null) {
            mGoogleApiClient.connect();
        }
    }

    // listeners for async database interactions
    @Override
    public void onDbSaveSuccess(String tag) {
        setViewModels(mTeams);
        resetListAdapter(mViewModels);
    }

    @Override
    public void onDbSaveFailure(String tag) {
        ErrorDialogs.showErrorDialog(this,
                getString(R.string.dialog_title_db_error),
                getString(R.string.dialog_message_db_error));
    }

    @Override
    public void onDbGetSuccess(String tag, List result) {
        setViewModels(result);
        resetListAdapter(mViewModels);
    }

    @Override
    public void onDbGetFailure(String tag) {
        ErrorDialogs.showErrorDialog(this,
                getString(R.string.dialog_title_db_error),
                getString(R.string.dialog_message_db_error));
    }
}
