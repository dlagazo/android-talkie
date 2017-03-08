package de.tubs.ibr.dtn.dtalkie;

import android.content.Context;
import android.net.Uri;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;

import de.tubs.ibr.dtn.dtalkie.db.Message;

public class MessageItem extends RelativeLayout {
    
    @SuppressWarnings("unused")
    private static final String TAG = "MessageItem";
    
    private Message mMessage = null;
    
    private TextView mLabel = null;
    private TextView mBottomText = null;
    private ImageView mIcon = null;
    private TextView mSideText = null;

    public MessageItem(Context context) {
        super(context);
    }

    public MessageItem(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mLabel = (TextView) findViewById(R.id.label);
        mBottomText = (TextView) findViewById(R.id.bottomtext);
        mIcon = (ImageView) findViewById(R.id.icon);
        mSideText = (TextView) findViewById(R.id.sidetext);
    }
    
    public void bind(Message m, int position) {
        mMessage = m;
        onDataChanged();
    }
    
    public void unbind() {
        // Clear all references to the message item, which can contain attachments and other
        // memory-intensive objects
    }
    
    public Message getMessage() {
        return mMessage;
    }
    
    private void onDataChanged() {

        String created = MessageAdapter.formatTimeStampString(getContext(), mMessage.getCreated().getTime());
        
        // set image mark
        mIcon.setImageLevel(mMessage.isMarked() ? 0 : 1);

        //mIcon.setImageURI(Uri.fromFile(new File(mMessage.getFile().getPath())));
        mLabel.setText(mMessage.getFile().getPath());
        mBottomText.setText(created);
        
        // get delay
        if (mMessage.getReceived() != null)
        {
            Long delay = mMessage.getReceived().getTime() - mMessage.getCreated().getTime();

            if (mMessage.getReceived().before(mMessage.getCreated()))
            {
                mSideText.setText("");
            }
            else if (delay <= 1000)
            {
                mSideText.setText("1 second");
            }
            else if (delay < 60000)
            {
                mSideText.setText((delay / 1000) + " seconds");
            }
            else if (delay < 120000)
            {
                mSideText.setText("~1 minute");
            }
            else
            {
                mSideText.setText("~" + delay / 60000 + " minutes");
            }
        }
        else
        {
            mSideText.setText("");
        }
    }
}
