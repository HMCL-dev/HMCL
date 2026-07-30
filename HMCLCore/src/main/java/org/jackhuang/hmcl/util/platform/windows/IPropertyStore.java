/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.util.platform.windows;

import com.sun.jna.Function;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/// Thin wrapper around the Windows [`IPropertyStore`](https://learn.microsoft.com/windows/win32/api/propsys/nn-propsys-ipropertystore)
/// COM interface.
///
/// Instances own one COM reference. Callers must release that reference with
/// [`#close()`] or [`#Release()`] when the store is no longer needed.
///
/// @author Glavo
@NotNullByDefault
public final class IPropertyStore implements AutoCloseable {

    /// Vtable slot of `IUnknown::AddRef`.
    private static final int VTBL_ADD_REF = 1;

    /// Vtable slot of `IUnknown::Release`.
    private static final int VTBL_RELEASE = 2;

    /// Vtable slot of `IPropertyStore::GetCount`.
    private static final int VTBL_GET_COUNT = 3;

    /// Vtable slot of `IPropertyStore::GetValue`.
    private static final int VTBL_GET_VALUE = 5;

    /// Vtable slot of `IPropertyStore::SetValue`.
    private static final int VTBL_SET_VALUE = 6;

    /// Vtable slot of `IPropertyStore::Commit`.
    private static final int VTBL_COMMIT = 7;

    /// The underlying COM interface pointer, or `null` after release.
    private @Nullable Pointer pointer;

    /// Wraps an existing `IPropertyStore` interface pointer.
    ///
    /// The wrapper takes ownership of one reference to `pointer`.
    ///
    /// @param pointer a non-null `IPropertyStore` interface pointer
    public IPropertyStore(Pointer pointer) {
        this.pointer = Objects.requireNonNull(pointer, "pointer");
    }

    /// Creates an `IPropertyStore` for the given window through
    /// [`Shell32#SHGetPropertyStoreForWindow(WinTypes.HANDLE, WinTypes.GUID, PointerByReference)`].
    ///
    /// @param shell32 the loaded Shell32 library
    /// @param hwnd the target window
    /// @return the property store, or `null` when the call fails
    public static @Nullable IPropertyStore forWindow(Shell32 shell32, WinTypes.HANDLE hwnd) {
        PointerByReference ppv = new PointerByReference();
        int hr = shell32.SHGetPropertyStoreForWindow(hwnd, WinTypes.GUID.IID_IPropertyStore, ppv);
        if (hr < 0 || ppv.getValue() == null) {
            return null;
        }
        return new IPropertyStore(ppv.getValue());
    }

    /// Creates an `IPropertyStore` for the given window handle value.
    ///
    /// @param shell32 the loaded Shell32 library
    /// @param hwnd the native window handle
    /// @return the property store, or `null` when the call fails
    public static @Nullable IPropertyStore forWindow(Shell32 shell32, long hwnd) {
        return forWindow(shell32, new WinTypes.HANDLE(Pointer.createConstant(hwnd)));
    }

    /// Returns the raw COM interface pointer.
    ///
    /// @return the interface pointer, or `null` after release
    public @Nullable Pointer getPointer() {
        return pointer;
    }

    /// Sets a property value, replacing any existing value for the same key.
    ///
    /// Changes are not persisted until [`#Commit()`] succeeds.
    ///
    /// @param key the property key to write
    /// @param propvar the value to assign; the store copies this value and does not take ownership of it
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/propsys/nf-propsys-ipropertystore-setvalue">IPropertyStore::SetValue method</a>
    public int SetValue(WinTypes.PROPERTYKEY key, WinTypes.PROPVARIANT propvar) {
        key.write();
        propvar.write();
        return invokeHResult(VTBL_SET_VALUE, key, propvar);
    }

    /// Saves property changes made by [`#SetValue(WinTypes.PROPERTYKEY, WinTypes.PROPVARIANT)`].
    ///
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/propsys/nf-propsys-ipropertystore-commit">IPropertyStore::Commit method</a>
    public int Commit() {
        return invokeHResult(VTBL_COMMIT);
    }

    /// Retrieves the value of a property.
    ///
    /// On success, the caller owns the resources held by `propvar` and must clear them,
    /// for example with [`Ole32#PropVariantClear(WinTypes.PROPVARIANT)`].
    ///
    /// @param key the property key to read
    /// @param propvar receives the property value
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/propsys/nf-propsys-ipropertystore-getvalue">IPropertyStore::GetValue method</a>
    public int GetValue(WinTypes.PROPERTYKEY key, WinTypes.PROPVARIANT propvar) {
        key.write();
        propvar.clear();
        propvar.write();
        int hr = invokeHResult(VTBL_GET_VALUE, key, propvar);
        propvar.read();
        return hr;
    }

    /// Returns the number of properties currently stored.
    ///
    /// @param count receives the property count on success
    /// @return `S_OK` if the operation succeeds; otherwise a failure `HRESULT`
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/propsys/nf-propsys-ipropertystore-getcount">IPropertyStore::GetCount method</a>
    public int GetCount(IntByReference count) {
        return invokeHResult(VTBL_GET_COUNT, count);
    }

    /// Increments the COM reference count.
    ///
    /// @return the new reference count; the return value is intended for diagnostics only
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/unknwn/nf-unknwn-iunknown-addref">IUnknown::AddRef method</a>
    public int AddRef() {
        return invokeInt(VTBL_ADD_REF);
    }

    /// Decrements the COM reference count and drops this wrapper's pointer when released.
    ///
    /// Repeated calls after the first successful release are no-ops and return `0`.
    ///
    /// @return the new reference count; the return value is intended for diagnostics only
    /// @see <a href="https://learn.microsoft.com/windows/win32/api/unknwn/nf-unknwn-iunknown-release">IUnknown::Release method</a>
    public int Release() {
        if (pointer == null) {
            return 0;
        }
        int remaining = invokeInt(VTBL_RELEASE);
        pointer = null;
        return remaining;
    }

    /// Releases the owned COM reference.
    @Override
    public void close() {
        Release();
    }

    /// Invokes a COM method that returns an `HRESULT`.
    ///
    /// @param vtableIndex the zero-based vtable slot
    /// @param args arguments after the implicit `this` pointer
    /// @return the method `HRESULT`
    private int invokeHResult(int vtableIndex, Object... args) {
        return invokeInt(vtableIndex, args);
    }

    /// Invokes a COM method that returns an `int`.
    ///
    /// @param vtableIndex the zero-based vtable slot
    /// @param args arguments after the implicit `this` pointer
    /// @return the integer return value
    private int invokeInt(int vtableIndex, Object... args) {
        Pointer thisPointer = requirePointer();
        Pointer vtable = thisPointer.getPointer(0);
        Pointer functionPointer = vtable.getPointer((long) vtableIndex * Native.POINTER_SIZE);
        Function function = Function.getFunction(functionPointer, Function.ALT_CONVENTION);

        Object[] fullArgs = new Object[args.length + 1];
        fullArgs[0] = thisPointer;
        System.arraycopy(args, 0, fullArgs, 1, args.length);
        return function.invokeInt(fullArgs);
    }

    /// Returns the live interface pointer or throws when already released.
    ///
    /// @return the interface pointer
    private Pointer requirePointer() {
        Pointer thisPointer = pointer;
        if (thisPointer == null) {
            throw new IllegalStateException("IPropertyStore has already been released");
        }
        return thisPointer;
    }
}
