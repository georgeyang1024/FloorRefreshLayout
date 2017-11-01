package cn.georgeyang.floorrefreshlayout;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.Random;

public class MainActivity extends AppCompatActivity implements FloorRefreshLayout.OnRefreshListener {

    FloorRefreshLayout floorRefreshLayout;
    RecyclerView recyclerView;
    Adapter adapter = new Adapter();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        floorRefreshLayout = (FloorRefreshLayout) findViewById(R.id.switcherLayout);
        floorRefreshLayout.setBackground(new VerticalCenterDrawable(BitmapFactory.decodeResource(getResources(),R.mipmap.home_refresh_reblo)));
        recyclerView = (RecyclerView) findViewById(R.id.recyclerview);
        floorRefreshLayout.setOnRefreshListener(this);
        floorRefreshLayout.setDoubleLoosenEnable(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onNormal() {

    }

    @Override
    public void onRefresh() {
        getWindow().getDecorView().postDelayed(new Runnable() {
            @Override
            public void run() {
                floorRefreshLayout.setRefreshing(false);
                adapter.notifyDataSetChanged();
            }
        },2500);
    }

    @Override
    public void onPullToNextPage(View view) {
        startActivity(new Intent(this,NextPageActivity.class));
    }


    class Adapter extends RecyclerView.Adapter<ViewHolder> {
        Random random = new Random();
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item,null));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            holder.textView.setText(position + "#" + random.nextDouble());
        }

        @Override
        public int getItemCount() {
            return 100;
        }
    }

    class ViewHolder extends RecyclerView.ViewHolder
    {
        private TextView textView;

        public ViewHolder(View itemView) {
            super(itemView);
            textView = (TextView) itemView.findViewById(R.id.text);
        }
    }
}
