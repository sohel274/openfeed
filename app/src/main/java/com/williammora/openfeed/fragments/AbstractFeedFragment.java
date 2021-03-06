package com.williammora.openfeed.fragments;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.williammora.openfeed.R;
import com.williammora.openfeed.activities.StatusActivity;
import com.williammora.openfeed.adapters.FeedAdapter;
import com.williammora.openfeed.dto.Feed;
import com.williammora.openfeed.listeners.FeedFragmentListener;
import com.williammora.openfeed.listeners.OnViewHolderClickListener;

import java.util.ArrayList;
import java.util.List;

import twitter4j.Paging;
import twitter4j.Status;

public abstract class AbstractFeedFragment extends OpenFeedFragment implements
        SwipeRefreshLayout.OnRefreshListener, RecyclerView.OnScrollListener,
        OnViewHolderClickListener<Status> {

    public final String TAG = getClass().getSimpleName();

    private static final String SAVED_FEED = "SAVED_FEED";

    private static final int STATUS_REQUEST_CODE = 100;

    protected RecyclerView mFeedView;
    protected FeedAdapter mAdapter;
    protected SwipeRefreshLayout mFeedContainer;
    protected Feed mFeed;
    protected Paging mPaging;
    protected boolean mRequestingMore;

    protected FeedFragmentListener mListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_feed, container, false);

        if (savedInstanceState != null) {
            mFeed = (Feed) savedInstanceState.getSerializable(SAVED_FEED);
        } else {
            mFeed = new Feed();
            mFeed.setStatuses(new ArrayList<Status>());
        }

        mAdapter = new FeedAdapter(mFeed.getStatuses(), this);

        mFeedView = (RecyclerView) rootView.findViewById(R.id.feed);
        mFeedView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mFeedView.setAdapter(mAdapter);
        mFeedView.setOnScrollListener(this);

        mFeedContainer = (SwipeRefreshLayout) rootView.findViewById(R.id.feed_container);
        mFeedContainer.setColorSchemeColors(
                getResources().getColor(R.color.openfeed_deep_orange_a400),
                getResources().getColor(R.color.openfeed_deep_orange_a700),
                getResources().getColor(R.color.openfeed_deep_orange_a200),
                getResources().getColor(R.color.openfeed_deep_orange_a100));
        mFeedContainer.setOnRefreshListener(this);

        return rootView;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (!(activity instanceof FeedFragmentListener)) {
            throw new ClassCastException("Activity must implement FeedFragmentListener");
        }
        mListener = (FeedFragmentListener) activity;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == STATUS_REQUEST_CODE && data != null) {
            Status status = (Status) data.getSerializableExtra(StatusActivity.EXTRA_STATUS);
            if (status != null) {
                updateStatus(status);
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mFeed.getStatuses().isEmpty()) {
            mFeedContainer.setRefreshing(true);
            onRefresh();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putSerializable(SAVED_FEED, mFeed);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onRefresh() {
        mPaging = new Paging();
        mPaging.count(50);
        if (!mFeed.getStatuses().isEmpty()) {
            mPaging.setSinceId(mFeed.getStatuses().get(0).getId());
        }
        requestMore(mPaging);
    }

    private void requestMore(Paging paging) {
        if (mRequestingMore) {
            return;
        }
        mRequestingMore = true;
        mFeedContainer.setEnabled(false);
        mFeedContainer.setRefreshing(true);
        doRequest(paging);
    }

    protected abstract void doRequest(Paging paging);

    public void onScrollStateChanged(int i) {
        mListener.onScrollStateChanged(i);
    }

    public void onScrolled(int x, int y) {
        mListener.showGoToTopOption(shouldShowGoToTopButton());
        if (mRequestingMore) {
            return;
        }
        if (feedBottomReached()) {
            requestPreviousStatuses();
        }
    }

    private boolean shouldShowGoToTopButton() {
        if (mFeedView.getChildCount() <= 0) {
            return false;
        }
        View topChildView = mFeedView.getChildAt(0);
        Status currentTopStatus = (Status) topChildView.getTag();
        int statusIndex = mFeed.getStatuses().indexOf(currentTopStatus);
        return statusIndex >= 4;
    }

    private boolean feedBottomReached() {
        // Since we assigned the Status as the View tag we need to compare whatever is last
        // on screen vs the last element on the status list
        View bottomChildView = mFeedView.getChildAt(mFeedView.getChildCount() - 1);
        if (bottomChildView == null) {
            return false;
        }
        Status currentBottomStatus = (Status) bottomChildView.getTag();
        Status lastStatus = mFeed.getStatuses().get(mFeed.getStatuses().size() - 1);

        return currentBottomStatus == lastStatus;
    }

    public void goToTop() {
        mFeedView.scrollToPosition(0);
        mListener.showGoToTopOption(false);
    }

    private void requestPreviousStatuses() {
        mPaging = new Paging();
        mPaging.setMaxId(mFeed.getStatuses().get(mFeed.getStatuses().size() - 1).getId());
        mPaging.count(100);
        requestMore(mPaging);
    }

    @Override
    public void onItemClick(View view, Status status) {
        Intent intent = new Intent();
        intent.setClass(getActivity(), StatusActivity.class);
        intent.putExtra(StatusActivity.EXTRA_STATUS, status);
        startActivityForResult(intent, STATUS_REQUEST_CODE);
    }

    protected void updateFeed(Feed feed) {

        List<Status> statuses = feed.getStatuses();

        if (statuses == null || statuses.isEmpty()) {
            return;
        }

        if (mFeed.getStatuses().isEmpty()) {
            // Timeline was empty
            statuses.addAll(mFeed.getStatuses());
            mFeed.setStatuses(statuses);
            mAdapter.setDataset(mFeed.getStatuses());
        } else if (mPaging.getMaxId() > 1) {
            // Previous statuses were requested
            statuses.remove(0);
            mFeed.getStatuses().addAll(statuses);
            mAdapter.addAll(statuses);
        } else {
            // Latest statuses were requested
            mFeed.getStatuses().addAll(0, statuses);
            mAdapter.addAll(0, statuses);
            mFeedView.smoothScrollToPosition(0);
        }

        mFeed.setPaging(mPaging);
    }

    public void onRequestCompleted() {
        mRequestingMore = false;
        mFeedContainer.setRefreshing(false);
        mFeedContainer.setEnabled(true);
    }

    protected void updateStatus(Status status) {
        Status retweetedStatus = status.getRetweetedStatus();
        for (int i = 0; i < mFeed.getStatuses().size(); i++) {
            Status s = mFeed.getStatuses().get(i);
            if (statusesMatch(s, status.isRetweet() ? retweetedStatus : status)) {
                mFeed.getStatuses().set(i, status);
                mAdapter.replace(i, status);
                break;
            }
        }
    }

    private boolean statusesMatch(Status s1, Status s2) {
        return !s1.isRetweet() ? s1.getId() == s2.getId() :
                s1.getRetweetedStatus().getId() == s2.getId();
    }
}
