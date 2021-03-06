/**
 *     Copyright 2016 Austin Keener
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.dv8tion.jda.player;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;

import net.dv8tion.jda.audio.AudioConnection;
import net.dv8tion.jda.audio.AudioSendHandler;
import net.dv8tion.jda.player.source.AudioSource;
import net.dv8tion.jda.player.source.AudioStream;
import net.dv8tion.jda.player.source.AudioTimestamp;
import net.dv8tion.jda.utils.SimpleLog;

public class MusicPlayer implements AudioSendHandler
{
    public static final int PCM_FRAME_SIZE = 4;
    protected LinkedList<AudioSource> audioQueue = new LinkedList<>();
    protected AudioSource previousAudioSource = null;
    protected AudioSource currentAudioSource = null;
    protected AudioStream currentAudioStream = null;
    protected State state = State.STOPPED;
    protected boolean autoContinue = true;
    protected boolean shuffle = false;
    protected boolean repeat = false;
    protected float volume = 1.0F;

    protected enum State
    {
        PLAYING, PAUSED, STOPPED;
    }

    public void setRepeat(boolean repeat)
    {
        this.repeat = repeat;
    }

    public boolean isRepeat()
    {
        return repeat;
    }

    public float getVolume()
    {
        return this.volume;
    }

    public void setVolume(float volume)
    {
        this.volume = volume;
    }

    public void setShuffle(boolean shuffle)
    {
        this.shuffle = shuffle;
    }

    public boolean isShuffle()
    {
        return shuffle;
    }

    public void reload(boolean autoPlay)
    {
        reload0(autoPlay, true);
    }

    public void skipToNext()
    {
        playNext(false);
        //TODO: fire onSkip
    }

    public LinkedList<AudioSource> getAudioQueue()
    {
        return audioQueue;
    }

    public AudioSource getCurrentAudioSource()
    {
        return currentAudioSource;
    }

    public AudioSource getPreviousAudioSource()
    {
        return previousAudioSource;
    }

    public AudioTimestamp getCurrentTimestamp()
    {
        if (currentAudioStream != null)
            return currentAudioStream.getCurrentTimestamp();
        else
            return null;
    }

    // ============ JDA Player interface overrides =============

    public void play()
    {
        play0(true);
    }

    public void pause()
    {
        if (state == State.PAUSED)
            return;

        if (state == State.STOPPED)
            throw new IllegalStateException("Cannot pause a stopped player!");

        state = State.PAUSED;
        //TODO: fire onPause
    }

    @Override
    public boolean canProvide()
    {
        return state.equals(State.PLAYING);
    }

    private byte[] buffer = new byte[AudioConnection.OPUS_FRAME_SIZE * PCM_FRAME_SIZE];

    @Override
    public byte[] provide20MsAudio()
    {
//        if (currentAudioStream == null || audioFormat == null)
//            throw new IllegalStateException("The Audio source was never set for this player!\n" +
//                    "Please provide an AudioInputStream using setAudioSource.");
        try
        {
            int amountRead = currentAudioStream.read(buffer, 0, buffer.length);
            if (amountRead > -1)
            {
                if (amountRead<buffer.length) {
                    Arrays.fill(buffer, amountRead, buffer.length - 1, (byte) 0);
                }
                if (volume != 1) {
                    for (int i = 0; i < buffer.length; i+=2) {
                        short sample = (short) ((buffer[i+1] & 0xff) | (buffer[i] << 8));
                        sample = (short) (sample * volume);
                        buffer[i+1] = (byte)(sample & 0xff);
                        buffer[i] = (byte)((sample >> 8) & 0xff);
                    }
                }
                return buffer;
            }
            else
            {
                if (autoContinue)
                {
                    if(repeat)
                    {
                        reload0(true, false);
                        //TODO: fire onRepeat
                    }
                    else
                    {
                        playNext(true);
                    }
                }
                else
                    stop0(true);
                return null;
            }
        }
        catch (IOException e)
        {
            SimpleLog.getLog("JDA-Player").log(e);
        }
        return null;
    }

    public void stop()
    {
        stop0(true);
    }

    public boolean isPlaying()
    {
        return state == State.PLAYING;
    }

    public boolean isPaused()
    {
        return state == State.PAUSED;
    }

    public boolean isStopped()
    {
        return state == State.STOPPED;
    }

    // ========= Internal Functions ==========

    protected void play0(boolean fireEvent)
    {
        if (state == State.PLAYING)
            return;

        if (currentAudioSource != null)
        {
            state = State.PLAYING;
            return;
        }

        if (audioQueue.isEmpty())
            throw new IllegalStateException("MusicPlayer: The audio queue is empty! Cannot start playing.");

        loadFromSource(audioQueue.removeFirst());

        state = State.PLAYING;
        //TODO: fire onPlaying
    }

    protected void stop0(boolean fireEvent)
    {
        if (state == State.STOPPED)
            return;

        state = State.STOPPED;
        try
        {
            currentAudioStream.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        finally
        {
            previousAudioSource = currentAudioSource;
            currentAudioSource = null;
            currentAudioStream = null;
        }
        //TODO: fire onStop
    }

    protected void reload0(boolean autoPlay, boolean fireEvent)
    {
        if (previousAudioSource == null && currentAudioSource == null)
            throw new IllegalStateException("Cannot restart or reload a player that has never been started!");

        stop0(false);
        loadFromSource(previousAudioSource);

        if (autoPlay)
            play0(false);

        //TODO: fire onReload
    }

    protected void playNext(boolean fireEvent)
    {
        if (audioQueue.isEmpty())
        {
            stop0(false);   //Maybe true?
            //TODO: fire onFinish
            return;
        }

        stop0(false);
        AudioSource source;
        if (shuffle)
        {
            Random rand = new Random();
            source = audioQueue.remove(rand.nextInt(audioQueue.size()));
        }
        else
            source = audioQueue.removeFirst();
        loadFromSource(source);

        play0(false);
        //TODO: fire onNext
    }

    protected void loadFromSource(AudioSource source)
    {
        AudioStream stream = source.asStream();
        currentAudioSource = source;
        currentAudioStream = stream;
    }
}
