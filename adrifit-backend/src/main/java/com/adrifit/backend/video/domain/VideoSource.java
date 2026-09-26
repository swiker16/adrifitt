package com.adrifit.backend.video.domain;

/** Who recorded the video. */
public enum VideoSource {
    /** The client's execution, waiting for the trainer's correction. */
    CLIENT,
    /** A demonstration sent by the trainer. */
    TRAINER
}
