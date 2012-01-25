
package org.jhrome;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.DragGestureListener;
import java.awt.dnd.DragSource;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragSourceListener;
import java.awt.dnd.DragSourceMotionListener;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import com.sun.awt.AWTUtilities;

public class JhromeTabbedPane extends JLayeredPane
{
	public JhromeTabbedPane( )
	{
		init( );
	}
	
	private List<TabInfo>	tabs	= new ArrayList<TabInfo>( );
	
	private static class TabInfo
	{
		ActionListener	closeButtonHandler;
		JhromeTab		tab;
		Dimension		prefSize;
		boolean			removing;
		
		int				targetX;
		int				targetWidth;
		Rectangle		targetBounds	= new Rectangle( );
		
		int				animX;
		int				animWidth;
		
		boolean			dragging;
		int				dragX;
		double			grabX;
	}
	
	private int					overlap				= 13;
	
	private double				animFactor			= 0.7;
	
	private javax.swing.Timer	animTimer;
	
	private TabLayoutManager	layout;
	
	private boolean				useUniformWidth		= false;
	private int					uniformWidth		= 300;
	
	private boolean				holdTabScale		= true;
	
	private MouseManager		mouseOverManager;
	
	private TabInfo				selectedTab			= null;
	
	private int					contentPanelOverlap	= 1;
	
	private JPanel				contentPanel;
	
	private int					tabMargin			= 2;
	
	private JButton				newTabButton;
	
	private ActionListener		newTabButtonListener;
	
	private static Window		dragImageWindow		= null;
	
	private static Image		dragImage			= null;
	
	private static double		grabX				= 0;
	private static JhromeTab	draggedTab			= null;
	
	private DragHandler			dragHandler;
	private DropHandler			dropHandler;
	
	private class MouseManager extends RecursiveListener
	{
		MouseAdapter	adapter	= new MouseAdapter( )
								{
									private void updateHoldTabScale( MouseEvent e )
									{
										Point p = e.getPoint( );
										p = SwingUtilities.convertPoint( ( Component ) e.getSource( ) , p , JhromeTabbedPane.this );
										boolean newMouseOver = layout.tabZone.contains( p );
										if( newMouseOver != holdTabScale )
										{
											holdTabScale = newMouseOver;
											invalidate( );
											validate( );
										}
									}
									
									@Override
									public void mouseEntered( MouseEvent e )
									{
										updateHoldTabScale( e );
									}
									
									@Override
									public void mouseExited( MouseEvent e )
									{
										updateHoldTabScale( e );
									}
									
									@Override
									public void mouseMoved( MouseEvent e )
									{
										updateHoldTabScale( e );
										
										Point p = SwingUtilities.convertPoint( e.getComponent( ) , e.getPoint( ) , JhromeTabbedPane.this );
										JhromeTab tab = getHoverableTabAt( p );
										for( TabInfo info : tabs )
										{
											info.tab.setRollover( info.tab == tab );
										}
									}
									
									@Override
									public void mousePressed( MouseEvent e )
									{
										Point p = SwingUtilities.convertPoint( e.getComponent( ) , e.getPoint( ) , JhromeTabbedPane.this );
										JhromeTab tab = getSelectableTabAt( p );
										if( tab != null )
										{
											setSelectedTab( tab );
										}
									}
								};
		
		@Override
		protected void install( Component c )
		{
			c.addMouseListener( adapter );
			c.addMouseMotionListener( adapter );
		}
		
		@Override
		protected void uninstall( Component c )
		{
			c.removeMouseListener( adapter );
			c.removeMouseMotionListener( adapter );
		}
	}
	
	private void init( )
	{
		animTimer = new Timer( 15 , new ActionListener( )
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				invalidate( );
				validate( );
			}
		} );
		layout = new TabLayoutManager( );
		setLayout( layout );
		
		invalidate( );
		validate( );
		
		mouseOverManager = new MouseManager( );
		mouseOverManager.install( this );
		
		contentPanel = new JPanel( );
		add( contentPanel );
		
		contentPanel.setOpaque( false );
		contentPanel.setLayout( new BorderLayout( ) );
		contentPanel.setBorder( new JhromeContentPanelBorder( ) );
		contentPanel.setBackground( JhromeTabBorder.SELECTED_BORDER.bottomColor );
		
		newTabButton = new JButton( "+" );
		add( newTabButton );
		
		newTabButtonListener = new ActionListener( )
		{
			@Override
			public void actionPerformed( ActionEvent e )
			{
				addTab( getTabCount( ) , new JhromeTab( "New Tab" ) );
			}
		};
		
		newTabButton.addActionListener( newTabButtonListener );
		
		dragHandler = new DragHandler( this , DnDConstants.ACTION_MOVE );
		dropHandler = new DropHandler( this );
	}
	
	private void setSelectedTab( TabInfo info )
	{
		if( selectedTab != info )
		{
			if( selectedTab != null )
			{
				selectedTab.tab.setSelected( false );
				contentPanel.removeAll( );
			}
			
			selectedTab = info;
			
			if( selectedTab != null )
			{
				selectedTab.tab.setSelected( true );
				if( selectedTab.tab.getContent( ) != null )
				{
					contentPanel.add( selectedTab.tab.getContent( ) , BorderLayout.CENTER );
				}
			}
			
			invalidate( );
			validate( );
		}
	}
	
	public int getTabCount( )
	{
		int count = 0;
		for( int i = 0 ; i < tabs.size( ) ; i++ )
		{
			if( !tabs.get( i ).removing )
			{
				count++ ;
			}
		}
		return count;
	}
	
	public void setSelectedTab( JhromeTab tab )
	{
		if( tab == null )
		{
			setSelectedTab( ( TabInfo ) null );
		}
		else
		{
			TabInfo info = getInfo( tab );
			if( info == null || info.removing )
			{
				throw new IllegalArgumentException( "tab must be a child of this " + getClass( ).getName( ) );
			}
			setSelectedTab( info );
		}
	}
	
	public JhromeTab getHoverableTabAt( Point p )
	{
		for( int i = -1 ; i < tabs.size( ) ; i++ )
		{
			TabInfo info = i < 0 ? selectedTab : tabs.get( i );
			if( info == null || info.removing )
			{
				continue;
			}
			if( info == selectedTab && i >= 0 )
			{
				continue;
			}
			JhromeTab tab = info.tab;
			Component c = tab.getRenderer( );
			Point converted = SwingUtilities.convertPoint( this , p , c );
			if( c.contains( converted ) )
			{
				if( tab.isHoverableAt( converted ) )
				{
					return tab;
				}
			}
		}
		return null;
	}
	
	public JhromeTab getDraggableTabAt( Point p )
	{
		for( int i = -1 ; i < tabs.size( ) ; i++ )
		{
			TabInfo info = i < 0 ? selectedTab : tabs.get( i );
			if( info == null || info.removing )
			{
				continue;
			}
			if( info == selectedTab && i >= 0 )
			{
				continue;
			}
			JhromeTab tab = info.tab;
			Component c = tab.getRenderer( );
			Point converted = SwingUtilities.convertPoint( this , p , c );
			if( c.contains( converted ) )
			{
				if( tab.isDraggableAt( converted ) )
				{
					return tab;
				}
			}
		}
		return null;
	}
	
	public JhromeTab getSelectableTabAt( Point p )
	{
		for( int i = -1 ; i < tabs.size( ) ; i++ )
		{
			TabInfo info = i < 0 ? selectedTab : tabs.get( i );
			if( info == null || info.removing )
			{
				continue;
			}
			if( info == selectedTab && i >= 0 )
			{
				continue;
			}
			JhromeTab tab = info.tab;
			Component c = tab.getRenderer( );
			Point converted = SwingUtilities.convertPoint( this , p , c );
			if( c.contains( converted ) )
			{
				if( tab.isSelectableAt( converted ) )
				{
					return tab;
				}
			}
		}
		return null;
	}
	
	private TabInfo getInfo( JhromeTab tab )
	{
		for( TabInfo info : tabs )
		{
			if( info.tab == tab )
			{
				return info;
			}
		}
		return null;
	}
	
	public int devirtualizeIndex( int index )
	{
		int virtual = 0;
		
		int devirtualized;
		for( devirtualized = 0 ; devirtualized < tabs.size( ) ; devirtualized++ )
		{
			if( virtual == index )
			{
				break;
			}
			if( !tabs.get( devirtualized ).removing )
			{
				virtual++ ;
			}
		}
		return devirtualized;
	}
	
	public void addTab( final JhromeTab tab )
	{
		addTab( getTabCount( ) , tab );
	}
	
	public void addTab( int index , final JhromeTab tab )
	{
		addTab( index , tab , true );
	}
	
	private void addTab( int index , final JhromeTab tab , boolean expand )
	{
		TabInfo info = new TabInfo( );
		info.tab = tab;
		info.prefSize = tab.getRenderer( ).getPreferredSize( );
		info.animWidth = 0;
		info.removing = false;
		if( tab.getCloseButton( ) != null )
		{
			info.closeButtonHandler = new ActionListener( )
			{
				@Override
				public void actionPerformed( ActionEvent e )
				{
					removeTab( tab );
				}
			};
			tab.getCloseButton( ).addActionListener( info.closeButtonHandler );
		}
		
		index = devirtualizeIndex( index );
		if( index > 0 )
		{
			TabInfo prev = tabs.get( index - 1 );
			info.animX = prev.animX + prev.animWidth - overlap;
		}
		tabs.add( index , info );
		add( tab.getRenderer( ) );
		
		animTimer.start( );
	}
	
	public void removeTab( JhromeTab tab )
	{
		TabInfo info = getInfo( tab );
		if( info != null )
		{
			if( info == selectedTab )
			{
				if( tabs.size( ) == 1 )
				{
					setSelectedTab( ( TabInfo ) null );
				}
				else
				{
					int index = tabs.indexOf( info );
					setSelectedTab( tabs.get( index < tabs.size( ) - 1 ? index + 1 : index - 1 ) );
				}
			}
			info.removing = true;
			animTimer.start( );
		}
	}
	
	private void actuallyRemoveTab( JhromeTab tab )
	{
		TabInfo info = getInfo( tab );
		if( info != null )
		{
			if( tab.getCloseButton( ) != null && info.closeButtonHandler != null )
			{
				tab.getCloseButton( ).removeActionListener( info.closeButtonHandler );
			}
			remove( tab.getRenderer( ) );
			if( tab.getContent( ) != null )
			{
				remove( tab.getContent( ) );
			}
			tabs.remove( info );
		}
	}
	
	private int getInsertIndex( int x )
	{
		int virtualIndex = 0;
		for( int i = 0 ; i < tabs.size( ) ; i++ )
		{
			TabInfo info = tabs.get( i );
			if( !info.removing )
			{
				if( x >= info.targetBounds.x && x <= info.targetBounds.x + info.targetBounds.width )
				{
					return virtualIndex;
				}
				virtualIndex++ ;
			}
		}
		return getTabCount( );
	}
	
	private void setDragState( JhromeTab draggedTab , double grabX , int dragX )
	{
		boolean validate = false;
		
		for( TabInfo info : tabs )
		{
			if( info.tab == draggedTab )
			{
				if( !info.dragging || info.grabX != grabX || info.dragX != dragX )
				{
					info.dragging = true;
					info.grabX = grabX;
					info.dragX = dragX;
					
					validate = true;
				}
			}
			else
			{
				if( info.dragging )
				{
					info.dragging = false;
					validate = true;
				}
			}
		}
		
		if( validate )
		{
			invalidate( );
			validate( );
		}
	}
	
	public JButton getNewTabButton( )
	{
		return null;
	}
	
	private int animate( int value , int target )
	{
		int d = value - target;
		d *= animFactor;
		return d == 0 ? target : target + d;
	}
	
	@SuppressWarnings( { "serial" } )
	private void showDragImageWindow( )
	{
		if( dragImageWindow == null )
		{
			dragImageWindow = new Window( null )
			{
				@Override
				public void paint( Graphics g )
				{
					Graphics2D g2 = ( Graphics2D ) g;
					
					if( dragImage != null )
					{
						g2.drawImage( dragImage , 0 , 0 , null );
					}
				}
			};
			
			AWTUtilities.setWindowOpaque( dragImageWindow , false );
		}
		
		if( dragImage != null )
		{
			dragImageWindow.setSize( dragImage.getWidth( null ) , dragImage.getHeight( null ) );
		}
		dragImageWindow.setVisible( true );
	}
	
	private void moveDragImageWindow( Point p )
	{
		if( dragImageWindow != null )
		{
			dragImageWindow.setLocation( p );
		}
	}
	
	private void disposeDragImageWindow( )
	{
		if( dragImageWindow != null )
		{
			dragImageWindow.dispose( );
			dragImageWindow = null;
			dragImage = null;
		}
	}
	
	private class TabLayoutManager implements LayoutManager
	{
		Dimension	lastSize		= null;
		
		int			animTotalWidth	= 0;
		
		Rectangle	tabZone			= new Rectangle( );
		
		@Override
		public void addLayoutComponent( String name , Component comp )
		{
		}
		
		@Override
		public void removeLayoutComponent( Component comp )
		{
		}
		
		@Override
		public Dimension preferredLayoutSize( Container parent )
		{
			return null;
		}
		
		@Override
		public Dimension minimumLayoutSize( Container parent )
		{
			return null;
		}
		
		@Override
		public void layoutContainer( Container parent )
		{
			Dimension size = getSize( );
			
			boolean reset = false;
			
			int width = getWidth( );
			int height = getHeight( );
			
			Insets insets = getInsets( );
			
			Dimension newTabButtonPrefSize = newTabButton.getPreferredSize( );
			
			int availWidth = width - insets.left - insets.right;
			int availTabWidth = width - insets.left - insets.right - tabMargin - tabMargin - overlap - newTabButtonPrefSize.width;
			int availHeight = height - insets.top - insets.bottom;
			
			boolean animNeeded = false;
			
			int x = 0;
			
			int newTotalWidth = 0;
			
			int tabHeight = newTabButtonPrefSize.height;
			
			boolean anyDragging = false;
			
			for( int i = 0 ; i < tabs.size( ) ; i++ )
			{
				TabInfo info = tabs.get( i );
				info.targetX = x;
				
				int targetWidth = info.removing ? 0 : useUniformWidth ? uniformWidth : info.prefSize.width;
				info.targetWidth = targetWidth;
				
				tabHeight = Math.max( tabHeight , info.prefSize.height );
				
				if( info.animWidth != targetWidth )
				{
					if( reset )
					{
						info.animWidth = targetWidth;
					}
					else
					{
						animNeeded = true;
						info.animWidth = animate( info.animWidth , targetWidth );
					}
				}
				
				if( info.animX != x )
				{
					if( reset )
					{
						info.animX = x;
					}
					else
					{
						animNeeded = true;
						info.animX = animate( info.animX , x );
					}
				}
				
				newTotalWidth = Math.max( newTotalWidth , info.targetX + info.animWidth );
				if( !info.dragging )
				{
					newTotalWidth = Math.max( newTotalWidth , info.animX + info.animWidth );
				}
				else
				{
					anyDragging = true;
				}
				
				if( !info.removing )
				{
					x += targetWidth;
				}
			}
			
			if( newTotalWidth > animTotalWidth )
			{
				animTotalWidth = newTotalWidth;
			}
			else if( !holdTabScale && !anyDragging && animTotalWidth != newTotalWidth )
			{
				if( reset )
				{
					animTotalWidth = newTotalWidth;
				}
				else
				{
					animNeeded = true;
					animTotalWidth = animate( animTotalWidth , newTotalWidth );
				}
			}
			
			double widthRatio = animTotalWidth > availTabWidth ? availTabWidth / ( double ) animTotalWidth : 1.0;
			
			double adjWidthRatio = widthRatio;
			for( int i = 0 ; i < tabs.size( ) ; i++ )
			{
				TabInfo info = tabs.get( i );
				if( info.removing )
				{
					continue;
				}
				Dimension minSize = info.tab.getRenderer( ).getMinimumSize( );
				if( minSize != null && info.animWidth >= minSize.width )
				{
					int targetWidth = useUniformWidth ? uniformWidth : info.prefSize.width;
					adjWidthRatio = Math.max( adjWidthRatio , minSize.width / ( double ) targetWidth );
				}
			}
			
			for( int i = 0 ; i < tabs.size( ) ; i++ )
			{
				TabInfo info = tabs.get( i );
				int tabX = insets.left + tabMargin + ( int ) ( info.animX * adjWidthRatio );
				int tabTargetX = insets.left + tabMargin + ( int ) ( info.targetX * adjWidthRatio );
				int tabW = ( int ) ( info.animWidth * adjWidthRatio ) + overlap;
				int tabTargetW = ( int ) ( info.targetWidth * adjWidthRatio ) + overlap;
				if( info.dragging )
				{
					tabX = info.dragX - ( int ) ( info.grabX * tabW );
					tabX = Math.max( tabX , insets.left + tabMargin );
					info.animX = ( int ) ( ( tabX - insets.left - tabMargin ) / adjWidthRatio );
				}
				info.tab.getRenderer( ).setBounds( tabX , insets.top , tabW , tabHeight );
				info.targetBounds.setFrame( tabTargetX , insets.top , tabTargetW , tabHeight );
			}
			
			tabZone.setFrame( insets.left , insets.top , availWidth , tabHeight );
			
			contentPanel.setBounds( insets.left , insets.top + tabHeight - contentPanelOverlap , availWidth , availHeight - tabHeight + contentPanelOverlap );
			
			int newTabButtonX = Math.min( insets.left + availWidth - tabMargin - newTabButtonPrefSize.width , insets.left + tabMargin + ( int ) ( newTotalWidth * adjWidthRatio ) + overlap );
			newTabButton.setBounds( newTabButtonX , insets.top , newTabButtonPrefSize.width , tabHeight );
			
			for( int i = tabs.size( ) - 1 ; i >= 0 ; i-- )
			{
				TabInfo info = tabs.get( i );
				if( info.removing && info.animWidth == 0 )
				{
					actuallyRemoveTab( info.tab );
				}
			}
			
			int layer = JLayeredPane.DEFAULT_LAYER;
			
			setComponentZOrder( newTabButton , layer++ );
			
			if( selectedTab != null )
			{
				setComponentZOrder( selectedTab.tab.getRenderer( ) , layer++ );
			}
			
			setComponentZOrder( contentPanel , layer++ );
			
			for( TabInfo info : tabs )
			{
				if( info == selectedTab )
				{
					continue;
				}
				setComponentZOrder( info.tab.getRenderer( ) , layer++ );
			}
			
			lastSize = size;
			
			if( animNeeded )
			{
				animTimer.start( );
			}
			else if( !animNeeded )
			{
				animTimer.stop( );
			}
		}
	}
	
	private class JhromeTransferable implements Transferable
	{
		@Override
		public DataFlavor[ ] getTransferDataFlavors( )
		{
			return null;
		}
		
		@Override
		public boolean isDataFlavorSupported( DataFlavor flavor )
		{
			return false;
		}
		
		@Override
		public Object getTransferData( DataFlavor flavor ) throws UnsupportedFlavorException , IOException
		{
			return null;
		}
	}
	
	private class DragHandler implements DragSourceListener,DragSourceMotionListener,DragGestureListener
	{
		DragSource	source;
		
		Point		dragOrigin;
		
		public DragHandler( Component comp , int actions )
		{
			source = new DragSource( );
			source.createDefaultDragGestureRecognizer( comp , actions , this );
			source.addDragSourceMotionListener( this );
		}
		
		@Override
		public void dragGestureRecognized( DragGestureEvent dge )
		{
			dragOrigin = dge.getDragOrigin( );
			
			draggedTab = getDraggableTabAt( dragOrigin );
			if( draggedTab != null )
			{
				Point p = SwingUtilities.convertPoint( JhromeTabbedPane.this , dragOrigin , draggedTab.getRenderer( ) );
				grabX = p.x / ( double ) draggedTab.getRenderer( ).getWidth( );
				Transferable t = new JhromeTransferable( );
				source.startDrag( dge , Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR ) , t , this );
			}
		}
		
		@Override
		public void dragEnter( DragSourceDragEvent dsde )
		{
		}
		
		@Override
		public void dragExit( DragSourceEvent dse )
		{
		}
		
		@Override
		public void dragOver( DragSourceDragEvent dsde )
		{
		}
		
		@Override
		public void dragMouseMoved( DragSourceDragEvent dsde )
		{
			final Point p = dsde.getLocation( );
			final Point pImage = new Point( p.x + 10 , p.y + 10 );
			
			showDragImageWindow( );
			moveDragImageWindow( pImage );
		}
		
		@Override
		public void dropActionChanged( DragSourceDragEvent dsde )
		{
		}
		
		@Override
		public void dragDropEnd( final DragSourceDropEvent dsde )
		{
			disposeDragImageWindow( );
		}
	}
	
	private class DropHandler implements DropTargetListener
	{
		DropTarget	target;
		
		public DropHandler( Component comp )
		{
			target = new DropTarget( comp , this );
		}
		
		private void handleDrag( DropTargetDragEvent dtde )
		{
		}
		
		@Override
		public void dragEnter( DropTargetDragEvent dtde )
		{
			handleDrag( dtde );
		}
		
		@Override
		public void dragOver( DropTargetDragEvent dtde )
		{
			handleDrag( dtde );
			
			if( draggedTab != null )
			{
				TabInfo info = getInfo( draggedTab );
				if( info != null )
				{
					int currentIndex = tabs.indexOf( info );
					int dragX = dtde.getLocation( ).x;
					setDragState( draggedTab , grabX , dragX );
					
					int insertX = dragX + ( int ) ( ( 0.5 - grabX ) * draggedTab.getRenderer( ).getWidth( ) );
					
					int newIndex = devirtualizeIndex( getInsertIndex( insertX ) );
					if( newIndex != currentIndex )
					{
						tabs.remove( info );
						newIndex = Math.min( newIndex , tabs.size( ) );
						tabs.add( newIndex , info );
						
						invalidate( );
						validate( );
					}
				}
			}
		}
		
		@Override
		public void dragExit( DropTargetEvent dte )
		{
			setDragState( null , 0 , 0 );
		}
		
		@Override
		public void dropActionChanged( DropTargetDragEvent dtde )
		{
			handleDrag( dtde );
		}
		
		@Override
		public void drop( DropTargetDropEvent dtde )
		{
			setDragState( null , 0 , 0 );
		}
	}
}
