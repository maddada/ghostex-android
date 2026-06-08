package com.termux.view;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalViewTest {

    @Test
    public void shouldFollowOutputAtScrollPositionWhenAtBottom() {
        assertTrue(TerminalView.shouldFollowOutputAtScrollPosition(0));
    }

    @Test
    public void shouldFollowOutputAtScrollPositionWhenVeryCloseToBottom() {
        assertTrue(TerminalView.shouldFollowOutputAtScrollPosition(-TerminalView.AUTO_SCROLL_BOTTOM_FOLLOW_THRESHOLD_ROWS));
    }

    @Test
    public void shouldNotFollowOutputAtScrollPositionWhenUserScrolledAwayFromBottom() {
        assertFalse(TerminalView.shouldFollowOutputAtScrollPosition(-TerminalView.AUTO_SCROLL_BOTTOM_FOLLOW_THRESHOLD_ROWS - 1));
    }
}
