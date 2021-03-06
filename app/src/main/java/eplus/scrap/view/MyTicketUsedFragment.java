package eplus.scrap.view;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import com.google.gson.Gson;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import eplus.scrap.EventBus.LoginEvent;
import eplus.scrap.R;
import eplus.scrap.adapter.MyTicketUsedAdapter;
import eplus.scrap.common.CommonFunc;
import eplus.scrap.common.SharePreferences;
import eplus.scrap.model.MyTicket;
import eplus.scrap.model.Pagination;
import eplus.scrap.model.Ticket;
import eplus.scrap.networking.BaseRestClient;
import eplus.scrap.networking.DataResponse;
import okhttp3.Call;
import okhttp3.Request;
import okhttp3.Response;
import static eplus.scrap.networking.ApiKey.GET_MY_TICKET;
import static eplus.scrap.networking.ApiKey.URL_LOGIN;
public class MyTicketUsedFragment extends Fragment implements View.OnClickListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static boolean m_iAmVisible;
    // TODO: Rename and change types of parameters
    private OnCompleteListener mListener;
    private ListView listView;
    private MyTicketUsedAdapter adapter;
    private ArrayList<Ticket> ticketArrayList;
    private String localTAG = "MyTicketFragment";
    private boolean flag_loading;
    private Pagination pagination;
    private LinearLayout lineErrMsg;
    private SwipeRefreshLayout swipeContainer;
    private RelativeLayout reNointernet;
    private LinearLayout lineBtRetry;
    private boolean onItemClick;
    private LinearLayout lgview;
    private Button btlgin;
    private FragmentActivity myContext;
    private boolean offline;
    private LinearLayout line_offline;
    private ImageView ic_close;

    private Context getCurrentContext(){
        return myContext == null ? getContext() : myContext;
    }
    public interface OnCompleteListener {
        void onRefresh();
    }
    public MyTicketUsedFragment() {
        // Required empty public constructor
    }
    // TODO: Rename and change types and number of parameters
    public static MyTicketUsedFragment newInstance(OnCompleteListener listener) {
        MyTicketUsedFragment fragment = new MyTicketUsedFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        fragment.mListener = listener;
        return fragment;
    }
    @Override
    public void setMenuVisibility(final boolean visible) {
        super.setMenuVisibility(visible);

    }
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        myContext = getActivity();
    }
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_my_ticket_used, container, false);
        line_offline = view.findViewById(R.id.line_msg_offline);
        ic_close = view.findViewById(R.id.img_close_msg);
        ic_close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                line_offline.setVisibility(View.GONE);
            }
        });
        listView = view.findViewById(R.id.listViewMyTicket);
        LinearLayout viewHeader = new LinearLayout(getCurrentContext());
        viewHeader.setOrientation(LinearLayout.HORIZONTAL);
        AbsListView.LayoutParams lp = new AbsListView.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, 200);
        viewHeader.setLayoutParams(lp);
        listView.addFooterView(viewHeader, null, false);
        lineErrMsg = view.findViewById(R.id.line_nodata);
        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            public void onScrollStateChanged(AbsListView view, int scrollState) {
            }
            public void onScroll(AbsListView view, int firstVisibleItem,
                                 int visibleItemCount, int totalItemCount) {
            }
        });
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Ticket ticket = ticketArrayList.get(position);
                //fillDataDetail(ticket);
                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                Fragment prev = getActivity().getSupportFragmentManager().findFragmentByTag("dialog_ticket_detail");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);
                TicketUsedDetailFragment fragment = new TicketUsedDetailFragment().newInstance(new TicketUsedDetailFragment.OnCompleteListener() {
                    @Override
                    public void onComplete() {
                        onItemClick = false;
                    }
                }, ticket);
                fragment.show(ft, "dialog_ticket_detail");
            }
        });
        swipeContainer = view.findViewById(R.id.swiperefresh);
        // Setup refresh listener which triggers new data loading
        swipeContainer.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                // Your code to refresh the list here.
                // Make sure you call swipeContainer.setRefreshing(false)
                // once the network request has completed successfully.
                swipeContainer.setRefreshing(false);
                if(CommonFunc.isNetworkConnected(getActivity())) {
                    getTicketUsed(false);
                    reNointernet.setVisibility(View.GONE);
                } else {
                    loadDataLocal();
                }
            }
        });
        reNointernet = view.findViewById(R.id.re_no_internet_layout);
        reNointernet.setVisibility(View.GONE);
        lineBtRetry = view.findViewById(R.id.line_bt_retry);
        lineBtRetry.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN){
                    PorterDuffColorFilter greyFilter = new PorterDuffColorFilter(CommonFunc.COLOR_HIGHLIGH, PorterDuff.Mode.MULTIPLY);
                    lineBtRetry.getBackground().setColorFilter(greyFilter);
                    Handler handler = new Handler();
                    handler.postDelayed(new Runnable() {
                        public void run() {
                            // yourMethod();
                            lineBtRetry.getBackground().clearColorFilter();
                        }
                    }, 1000);
                }
                else if(event.getAction() == MotionEvent.ACTION_UP){
                    lineBtRetry.getBackground().clearColorFilter();
                    if(CommonFunc.isNetworkConnected(getActivity())) {
                        getTicketUsed(true);
                        reNointernet.setVisibility(View.GONE);
                    } else {
                        loadDataLocal();
                    }
                }
                return true;
            }
        });
        initlogin(view);
        if (!SharePreferences.getStringPreference(getCurrentContext(), "token").isEmpty()) {
            lgview.setVisibility(View.GONE);
            if(CommonFunc.isNetworkConnected(getActivity())) {
                if (ticketArrayList != null && ticketArrayList.size() > 0) {
                    updateData();
                } else{
                    getTicketUsed(true);
                }
            } else {
                reNointernet.setVisibility(View.VISIBLE);
                loadDataLocal();
                offline = true;
            }
        }
        return view;
    }
    private void initlogin(View view) {
        lgview = view.findViewById(R.id.re_login);
        btlgin = view.findViewById(R.id.bt_login);
        btlgin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                lgview.setVisibility(View.GONE);
                reNointernet.setVisibility(View.GONE);
                FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                Fragment prev = getChildFragmentManager().findFragmentByTag("webview");
                if (prev != null) {
                    ft.remove(prev);
                }
                ft.addToBackStack(null);
                WebViewFragment fragment = new WebViewFragment().newInstance(new WebViewFragment.OnCompleteListener() {
                    @Override
                    public void onComplete() {
                        if (!SharePreferences.getStringPreference(getCurrentContext(), "token").isEmpty()) {
                            getActivity().runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    lgview.setVisibility(View.GONE);
                                    mListener.onRefresh();
                                }
                            });
                        } else lgview.setVisibility(View.VISIBLE);
                    }
                }, URL_LOGIN + "&callback_url=scrap-app%3A//login-on",true,false);
                fragment.show(ft, "webview");
            }
        });
    }
    private void loadDataLocal() {
        String data = MyTicket.getData_used(getCurrentContext());
        try {
            JSONArray jsonArray = new JSONArray(data);
            Gson gson = new Gson();
            ticketArrayList = new ArrayList<>();
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject json_data = jsonArray.getJSONObject(i);
                Ticket ticket = gson.fromJson(json_data.toString(), Ticket.class);
                ticketArrayList.add(ticket);
            }
            if (ticketArrayList.size() > 0) {
                flag_loading = true;
                line_offline.setVisibility(View.VISIBLE);
                reNointernet.setVisibility(View.GONE);
                updateData();
            } else {
                reNointernet.setVisibility(View.VISIBLE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void updateData() {
        if (ticketArrayList.size() > 0) {
            adapter = new MyTicketUsedAdapter(ticketArrayList, getActivity());
            listView.setAdapter(adapter);
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    if (!onItemClick) {
                        onItemClick = true;
                        Ticket ticket = ticketArrayList.get(position);
                        //fillDataDetail(ticket);
                        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
                        Fragment prev = getActivity().getSupportFragmentManager().findFragmentByTag("dialog_ticket_detail");
                        if (prev != null) {
                            ft.remove(prev);
                        }
                        ft.addToBackStack(null);
                        TicketDetailFragment fragment = new TicketDetailFragment().newInstance(new TicketDetailFragment.OnCompleteListener() {
                            @Override
                            public void onComplete() {
                                onItemClick = false;
                            }
                        }, ticket);
                        fragment.show(ft, "dialog_ticket_detail");
                    }
                }
            });
            adapter.notifyDataSetChanged();
            lineErrMsg.setVisibility(View.GONE);
            swipeContainer.setVisibility(View.VISIBLE);
        } else {
            lineErrMsg.setVisibility(View.VISIBLE);
            swipeContainer.setVisibility(View.GONE);
        }
    }
    private void loadmoreTicket() {
        flag_loading = false;
        String token = SharePreferences.getStringPreference(getCurrentContext(), "token");
        HashMap<String, String> hearder = new HashMap<>();
        if (!token.isEmpty()) {
            hearder.put("token", token);
        }
        HashMap<String, String> params = new HashMap<>();
        params.put("filter_type", "3,4,5");
        params.put("items_per_page", "10");
        int page = pagination.getPage() + 1;
        params.put("page", "" + page);
        DataResponse responseVeryfy = new DataResponse(getCurrentContext()) {
            @Override
            public void onRealFail() {
                reNointernet.setVisibility(View.VISIBLE);
                swipeContainer.setVisibility(View.GONE);
                offline = true;
            }
            @Override
            public void onRealSuccess(String response) {
                super.onRealSuccess(response);
                try {
                    JSONObject jObject = new JSONObject(response);
                    if (jObject.getString("status").equals("200")) {
                        Boolean success = jObject.getBoolean("success");
                        if (success) {
                            Gson gson = new Gson();
                            JSONObject pagine = jObject.getJSONObject("params");
                            pagination = gson.fromJson(pagine.toString(), Pagination.class);
                            JSONArray arrayEvent = jObject.getJSONArray("data");
                            if (arrayEvent.length() > 0) {
                                try {
                                    for (int i = 0; i < arrayEvent.length(); i++) {
                                        JSONObject json_data = arrayEvent.getJSONObject(i);
                                        Ticket ticket = gson.fromJson(json_data.toString(), Ticket.class);
                                        ticketArrayList.add(ticket);
                                    }
                                } finally {
                                }
                                String json = gson.toJson(ticketArrayList);
                                MyTicket.save_used(getCurrentContext(),json);
                                flag_loading = true;
                                adapter.notifyDataSetChanged();
                            } else {
                                flag_loading = false;
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 200) {
                    super.onResponse(call, response);
                } else {
                    super.onFailure(call, new IOException());
                }
            }
            @Override
            public void onFailure(Call call, IOException e) {
                super.onFailure(call, e);
            }
            @Override
            public void onFailure(Request request, IOException e) {
                super.onFailure(request, e);
            }
        };
        BaseRestClient.get(getActivity(), GET_MY_TICKET, params, hearder, responseVeryfy, false);
    }
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        m_iAmVisible = isVisibleToUser;

    }
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (!getUserVisibleHint()) {
            return;
        }
    }
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(LoginEvent event) {
        if (!SharePreferences.getStringPreference(getCurrentContext(), "token").isEmpty()) {
            lgview.setVisibility(View.GONE);
            if(CommonFunc.isNetworkConnected(getCurrentContext())) {

                getTicketUsed(true);
            } else {
                reNointernet.setVisibility(View.VISIBLE);
                loadDataLocal();
                offline = true;
            }
        }else {
            lgview.setVisibility(View.VISIBLE);
            swipeContainer.setVisibility(View.GONE);
        }
    }
    @Override
    public void onStart() {
        super.onStart();
        //Đăng ký lắng nghe
        EventBus.getDefault().register(this);
    }

    @Override
    public void onStop() {
        //Hủy lắng ngh]\
        EventBus.getDefault().unregister(this);
        super.onStop();
    }
    private void getTicketUsed(boolean loading) {
        String token = SharePreferences.getStringPreference(getCurrentContext(), "token");
        if(!token.isEmpty()) {
            HashMap<String, String> hearder = new HashMap<>();
            if (!token.isEmpty()) {
                hearder.put("token", token);
            }
            HashMap<String, String> params = new HashMap<>();
            params.put("filter_type", "3,4,5");
            //params.put("items_per_page", "10");
            DataResponse responseVeryfy = new DataResponse(getCurrentContext()) {
                @Override
                public void onRealFail() {
                    reNointernet.setVisibility(View.VISIBLE);
                    swipeContainer.setVisibility(View.GONE);
                    offline = true;
                }
                @Override
                public void onRealSuccess(String response) {
                    super.onRealSuccess(response);
                    try {
                        JSONObject jObject = new JSONObject(response);
                        if (jObject.getString("status").equals("200")) {
                            Boolean success = jObject.getBoolean("success");
                            if (success) {
                                Gson gson = new Gson();
                                JSONObject pagine = jObject.getJSONObject("params");
                                pagination = gson.fromJson(pagine.toString(), Pagination.class);
                                JSONArray arrayEvent = jObject.getJSONArray("data");
                                ticketArrayList = new ArrayList<>();
                                try {
                                    for (int i = 0; i < arrayEvent.length(); i++) {
                                        JSONObject json_data = arrayEvent.getJSONObject(i);
                                        Ticket ticket = gson.fromJson(json_data.toString(), Ticket.class);
                                        ticketArrayList.add(ticket);
                                    }
                                } finally {
                                }
                                String json = gson.toJson(ticketArrayList);
                                MyTicket.save_used(getCurrentContext(),json);
                                flag_loading = true;
                                if(line_offline.getVisibility() == View.VISIBLE) {
                                    line_offline.setVisibility(View.GONE);
                                }
                                updateData();
                            }
                        } else {
                            swipeContainer.setVisibility(View.GONE);
                            lineErrMsg.setVisibility(View.VISIBLE);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (response.code() == 200) {
                        super.onResponse(call, response);
                    } else {
                        super.onFailure(call, new IOException());
                    }
                }
                @Override
                public void onFailure(Call call, IOException e) {
                    super.onFailure(call, e);
                }
                @Override
                public void onFailure(Request request, IOException e) {
                    super.onFailure(request, e);
                }
            };
            BaseRestClient.get(getActivity(), GET_MY_TICKET, params, hearder, responseVeryfy, loading);
        }
    }
    // TODO: Rename method, update argument and hook method into UI event
    public void onButtonPressed(Uri uri) {
    }
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        myContext = (FragmentActivity) context;
    }
    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
    @Override
    public void onClick(View v) {
    }
    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating.html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFragmentInteractionListener {
        // TODO: Update argument type and name
        void onFragmentInteraction(Uri uri);
    }
}