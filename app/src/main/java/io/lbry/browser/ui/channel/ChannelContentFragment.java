package io.lbry.browser.ui.channel;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import io.lbry.browser.FileViewActivity;
import io.lbry.browser.MainActivity;
import io.lbry.browser.R;
import io.lbry.browser.adapter.ClaimListAdapter;
import io.lbry.browser.dialog.ContentFromDialogFragment;
import io.lbry.browser.dialog.ContentSortDialogFragment;
import io.lbry.browser.model.Claim;
import io.lbry.browser.tasks.ClaimSearchTask;
import io.lbry.browser.utils.Helper;
import io.lbry.browser.utils.Lbry;
import lombok.Setter;

public class ChannelContentFragment extends Fragment {

    @Setter
    private String channelId;
    private View sortLink;
    private View contentFromLink;
    private TextView sortLinkText;
    private TextView contentFromLinkText;
    private RecyclerView contentList;
    private int currentSortBy;
    private int currentContentFrom;
    private String contentReleaseTime;
    private List<String> contentSortOrder;
    private View contentLoading;
    private View bigContentLoading;
    private ClaimListAdapter contentListAdapter;
    private boolean contentClaimSearchLoading;
    private boolean contentHasReachedEnd;
    private int currentClaimSearchPage;
    private ClaimSearchTask contentClaimSearchTask;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_channel_content, container, false);

        currentSortBy = ContentSortDialogFragment.ITEM_SORT_BY_TRENDING;
        currentContentFrom = ContentFromDialogFragment.ITEM_FROM_PAST_WEEK;

        sortLink = root.findViewById(R.id.channel_content_sort_link);
        contentFromLink = root.findViewById(R.id.channel_content_time_link);

        sortLinkText = root.findViewById(R.id.channel_content_sort_link_text);
        contentFromLinkText = root.findViewById(R.id.channel_content_time_link_text);

        bigContentLoading = root.findViewById(R.id.channel_content_main_progress);
        contentLoading = root.findViewById(R.id.channel_content_load_progress);

        contentList = root.findViewById(R.id.channel_content_list);
        LinearLayoutManager llm = new LinearLayoutManager(getContext());
        contentList.setLayoutManager(llm);
        contentList.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (contentClaimSearchLoading) {
                    return;
                }

                LinearLayoutManager lm = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (lm != null) {
                    int visibleItemCount = lm.getChildCount();
                    int totalItemCount = lm.getItemCount();
                    int pastVisibleItems = lm.findFirstVisibleItemPosition();
                    if (pastVisibleItems + visibleItemCount >= totalItemCount) {
                        if (!contentHasReachedEnd) {
                            // load more
                            currentClaimSearchPage++;
                            fetchClaimSearchContent();
                        }
                    }
                }
            }
        });

        sortLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ContentSortDialogFragment dialog = ContentSortDialogFragment.newInstance();
                dialog.setCurrentSortByItem(currentSortBy);
                dialog.setSortByListener(new ContentSortDialogFragment.SortByListener() {
                    @Override
                    public void onSortByItemSelected(int sortBy) {
                        onSortByChanged(sortBy);
                    }
                });

                Context context = getContext();
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    dialog.show(activity.getSupportFragmentManager(), ContentSortDialogFragment.TAG);
                }
            }
        });
        contentFromLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ContentFromDialogFragment dialog = ContentFromDialogFragment.newInstance();
                dialog.setCurrentFromItem(currentContentFrom);
                dialog.setContentFromListener(new ContentFromDialogFragment.ContentFromListener() {
                    @Override
                    public void onContentFromItemSelected(int contentFromItem) {
                        onContentFromChanged(contentFromItem);
                    }
                });
                Context context = getContext();
                if (context instanceof MainActivity) {
                    MainActivity activity = (MainActivity) context;
                    dialog.show(activity.getSupportFragmentManager(), ContentFromDialogFragment.TAG);
                }
            }
        });

        return root;
    }

    private void onContentFromChanged(int contentFrom) {
        currentContentFrom = contentFrom;

        // rebuild options and search
        updateContentFromLinkText();
        contentReleaseTime = Helper.buildReleaseTime(currentContentFrom);
        fetchClaimSearchContent(true);
    }

    private void onSortByChanged(int sortBy) {
        currentSortBy = sortBy;

        // rebuild options and search
        Helper.setViewVisibility(contentFromLink, currentSortBy == ContentSortDialogFragment.ITEM_SORT_BY_TOP ? View.VISIBLE : View.GONE);
        currentContentFrom = currentSortBy == ContentSortDialogFragment.ITEM_SORT_BY_TOP ?
                (currentContentFrom == 0 ? ContentFromDialogFragment.ITEM_FROM_PAST_WEEK : currentContentFrom) : 0;

        updateSortByLinkText();
        contentSortOrder = Helper.buildContentSortOrder(currentSortBy);
        contentReleaseTime = Helper.buildReleaseTime(currentContentFrom);
        fetchClaimSearchContent(true);
    }

    private void updateSortByLinkText() {
        int stringResourceId = -1;
        switch (currentSortBy) {
            case ContentSortDialogFragment.ITEM_SORT_BY_NEW: default: stringResourceId = R.string.new_text; break;
            case ContentSortDialogFragment.ITEM_SORT_BY_TOP: stringResourceId = R.string.top; break;
            case ContentSortDialogFragment.ITEM_SORT_BY_TRENDING: stringResourceId = R.string.trending; break;
        }

        Helper.setViewText(sortLinkText, stringResourceId);
    }

    private void updateContentFromLinkText() {
        int stringResourceId = -1;
        switch (currentContentFrom) {
            case ContentFromDialogFragment.ITEM_FROM_PAST_24_HOURS: stringResourceId = R.string.past_24_hours; break;
            case ContentFromDialogFragment.ITEM_FROM_PAST_WEEK: default: stringResourceId = R.string.past_week; break;
            case ContentFromDialogFragment.ITEM_FROM_PAST_MONTH: stringResourceId = R.string.past_month; break;
            case ContentFromDialogFragment.ITEM_FROM_PAST_YEAR: stringResourceId = R.string.past_year; break;
            case ContentFromDialogFragment.ITEM_FROM_ALL_TIME: stringResourceId = R.string.all_time; break;
        }

        Helper.setViewText(contentFromLinkText, stringResourceId);
    }

    public void onResume() {
        super.onResume();
        fetchClaimSearchContent();
    }

    public void refresh() {
        fetchClaimSearchContent(true);
    }

    private Map<String, Object> buildContentOptions() {
        return Lbry.buildClaimSearchOptions(
                Claim.TYPE_STREAM,
                null,
                null, // TODO: Check mature
                Arrays.asList(channelId),
                null,
                getContentSortOrder(),
                contentReleaseTime,
                currentClaimSearchPage == 0 ? 1 : currentClaimSearchPage,
                Helper.CONTENT_PAGE_SIZE);
    }

    private List<String> getContentSortOrder() {
        if (contentSortOrder == null) {
            return Arrays.asList(Claim.ORDER_BY_RELEASE_TIME);
        }
        return contentSortOrder;
    }

    private View getLoadingView() {
        return (contentListAdapter == null || contentListAdapter.getItemCount() == 0) ? bigContentLoading : contentLoading;
    }

    private void fetchClaimSearchContent() {
        fetchClaimSearchContent(false);
    }

    private void fetchClaimSearchContent(boolean reset) {
        if (reset && contentListAdapter != null) {
            contentListAdapter.clearItems();
            currentClaimSearchPage = 1;
        }

        contentClaimSearchLoading = true;
        Map<String, Object> claimSearchOptions = buildContentOptions();
        contentClaimSearchTask = new ClaimSearchTask(claimSearchOptions, Lbry.LBRY_TV_CONNECTION_STRING, getLoadingView(), new ClaimSearchTask.ClaimSearchResultHandler() {
            @Override
            public void onSuccess(List<Claim> claims, boolean hasReachedEnd) {
                if (contentListAdapter == null) {
                    contentListAdapter = new ClaimListAdapter(claims, getContext());
                    contentListAdapter.setListener(new ClaimListAdapter.ClaimListItemListener() {
                        @Override
                        public void onClaimClicked(Claim claim) {
                            String claimId = claim.getClaimId();
                            String url = claim.getPermanentUrl();
                            if (claim.getName().startsWith("@")) {
                                // channel claim
                                Context context = getContext();
                                if (context instanceof MainActivity) {
                                    ((MainActivity) context).openChannelClaim(claim);
                                }
                            } else {
                                Intent intent = new Intent(getContext(), FileViewActivity.class);
                                intent.putExtra("claimId", claimId);
                                intent.putExtra("url", url);
                                MainActivity.startingFileViewActivity = true;
                                startActivity(intent);
                            }
                        }
                    });
                } else {
                    contentListAdapter.addItems(claims);
                }

                if (contentList != null && contentList.getAdapter() == null) {
                    contentList.setAdapter(contentListAdapter);
                }

                contentHasReachedEnd = hasReachedEnd;
                contentClaimSearchLoading = false;
            }

            @Override
            public void onError(Exception error) {
                contentClaimSearchLoading = false;
            }
        });
        contentClaimSearchTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
