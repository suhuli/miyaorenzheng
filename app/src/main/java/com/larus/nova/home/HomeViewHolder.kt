package com.larus.nova.home

import android.view.View
import androidx.viewbinding.ViewBinding
import com.larus.nova.util.ViewBindingViewHolder

abstract class HomeViewHolder<T, VB : ViewBinding>(itemView: View, binding: VB) : ViewBindingViewHolder<T, VB, HomeAdapter.Listener>(itemView, binding)